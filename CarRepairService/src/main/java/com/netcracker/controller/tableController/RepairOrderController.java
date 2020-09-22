package com.netcracker.controller.tableController;

import com.netcracker.entity.RepairOrder;
import com.netcracker.exception.ResourceNotFoundException;
import com.netcracker.pojoServices.pojoForFirstService.HttpDtoToAcceptRepair;
import com.netcracker.pojoServices.pojoForFirstService.HttpDtoToFinishRepair;
import com.netcracker.repository.RepairOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class RepairOrderController {

    @Autowired
    private RepairOrderRepository repairOrderRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private HttpHeaders headers;

    @Autowired
    private TaskScheduler taskScheduler;

    private List<String> listOfMasters = Arrays.asList("Alex", "Bob", "John", "James", "Chris", "Paul",
            "Serj", "Leo", "Howard", "Tony");

    private List<String> listOfBrokenDetails = Arrays.asList("Windshield", "Bumper", "Fender", "Headlight",
            "Hood", "Wheel", "Tire", "Transmission");

    private int randomPrice;

    private int randomMaster;

    private int randomBrokenDetail;

    private int randomValueForDateRepair;

    private final static int ONE_DAY = 24*60*60*1000;

    @Value("${app.endpoint}")
    private String urlService1;

    @GetMapping("/secondService/{id}")
    public ResponseEntity<HttpDtoToFinishRepair> getRepairOrderById(@PathVariable(value = "id") Integer repairOrderId)
            throws ResourceNotFoundException {
        RepairOrder repairOrder = repairOrderRepository.findById(repairOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Repair order not found with id: " + repairOrderId));
        HttpDtoToFinishRepair postDTOToService1 = HttpDtoToFinishRepair.create(repairOrder.getPrice());
        return ResponseEntity.ok().body(postDTOToService1);
    }

    @PostMapping("/secondService")
    public ResponseEntity<HttpDtoToAcceptRepair> createRepairOrder(@RequestBody HttpDtoToAcceptRepair postDTOFromService1)
            throws ResourceAccessException {
        randomPrice = (int)(Math.random() * 1000 + 1);
        randomMaster = (int) (Math.random() * 10);
        randomBrokenDetail = (int) (Math.random() * 8);
        randomValueForDateRepair = (int) (Math.random() * 20);

        //сохраняем переданный с первого сервиса заказ
        RepairOrder repairOrder = new RepairOrder(new Date(postDTOFromService1.getDay().getTime() + ONE_DAY * randomValueForDateRepair),
                randomPrice, listOfMasters.get(randomMaster), listOfBrokenDetails.get(randomBrokenDetail) , postDTOFromService1.getRowId());
        final RepairOrder createdRepairOrder = repairOrderRepository.save(repairOrder);

        HttpDtoToAcceptRepair responseToService1 = HttpDtoToAcceptRepair.create(createdRepairOrder.getId(), createdRepairOrder.getEndRepairDay());

        //отсылаем цену выполненого заказа первому сервису, после чего первый сервис закрывает заказ на ремонт. Отправляем через 90 секунд.
        taskScheduler.schedule(() -> {
            HttpDtoToFinishRepair putDTOFromService2 = HttpDtoToFinishRepair.create(createdRepairOrder.getPrice());
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<HttpDtoToFinishRepair> request = new HttpEntity<>(putDTOFromService2, headers);
            ResponseEntity<HttpDtoToFinishRepair> responseFromService1 = restTemplate
                    .exchange(urlService1 + "/" + createdRepairOrder.getRepairIdInternal() + "/finished", HttpMethod.PUT, request, HttpDtoToFinishRepair.class);
            },
            new Date(OffsetDateTime.now().plusSeconds(90).toInstant().toEpochMilli())
        );

        return ResponseEntity.ok().body(responseToService1);
    }

}
