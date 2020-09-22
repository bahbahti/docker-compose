package com.netcracker.controller.tableController;

import com.netcracker.converter.RepairOrderMapper;
import com.netcracker.dto.RepairOrderDTO;
import com.netcracker.entity.Car;
import com.netcracker.entity.Order;
import com.netcracker.entity.RepairOrder;
import com.netcracker.entity.enums.RepairStatus;
import com.netcracker.exception.BadRequestException;
import com.netcracker.exception.ResourceNotFoundException;
import com.netcracker.pojoServices.pojoForSecondService.HttpDtoToAcceptRepair;
import com.netcracker.pojoServices.pojoForSecondService.HttpDtoToFinishRepair;
import com.netcracker.repository.CarRepository;
import com.netcracker.repository.CustomerRepository;
import com.netcracker.repository.OrderRepository;
import com.netcracker.repository.RepairOrderRepository;
import com.netcracker.repository.filtering.RepairRepositoryForFilterQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.List;

@RestController
public class RepairOrderController {

    @Autowired
    private HttpHeaders headers;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RepairOrderMapper repairOrderMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RepairOrderRepository repairOrderRepository;

    @Autowired
    private RepairRepositoryForFilterQuery repairRepositoryForFilterQuery;

    @Value("${app.endpoint}")
    private String urlService2;

    @GetMapping("/repairOrders")
    public ResponseEntity<List<RepairOrderDTO>> getAllRepairOrders(@RequestParam(name = "id", required = false) List<Integer> id, @RequestParam(name = "carId", required = false) List<Integer> carId,
                                                                   @RequestParam(name = "customerId", required = false) List<Integer> customerId, @RequestParam(name = "idOfExternalTable", required = false) List<Integer> repairIdExternal,
                                                                   @RequestParam(name = "price", required = false) List<Integer> price, @RequestParam(name = "repairStatus", required = false, defaultValue = "") List<RepairStatus> repairStatus,
                                                                   @RequestParam(name = "startRepairDay", required = false) List<Date> startRepairDay, @RequestParam(name = "endRepairDay", required = false) List<Date> endRepairDay) {
        List<RepairOrder> repairOrders = repairRepositoryForFilterQuery.queryFunction(id, carId, customerId, repairIdExternal, price, repairStatus, startRepairDay, endRepairDay);
        List<RepairOrderDTO> repairOrdersDTO = repairOrderMapper.toRepairOrderDTOs(repairOrders);
        return ResponseEntity.ok().body(repairOrdersDTO);
    }

    @GetMapping("/repairOrders/{id}")
    public ResponseEntity<RepairOrderDTO> getRepairOrderById(@PathVariable(value = "id") Integer repairOrderId)
            throws ResourceNotFoundException {
        RepairOrder repairOrder = repairOrderRepository.findById(repairOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Repair order not found with id: " + repairOrderId));
        RepairOrderDTO repairOrderDTO = repairOrderMapper.toRepairOrderDTO(repairOrder);
        return ResponseEntity.ok().body(repairOrderDTO);
    }

    @PostMapping("/repairOrders")
    public ResponseEntity<RepairOrderDTO> createRepairOrder(@Valid @RequestBody RepairOrderDTO repairOrderDTOToSave)
            throws MethodArgumentNotValidException, HttpMessageNotReadableException,
            BadRequestException, ResourceNotFoundException {
        if(repairOrderDTOToSave.getId() != null && repairOrderRepository.findById(repairOrderDTOToSave.getId()).isPresent()) {
            throw new BadRequestException("Such id already exists");
        }
        if(!repairOrderDTOToSave.getRepairStatus().equals(RepairStatus.PENDING)) {
            throw new BadRequestException("Repair status should be PENDING");
        }
        //сохраняем во внутреннюю БД
        RepairOrder repairOrderToSave = repairOrderMapper.toRepairOrder(repairOrderDTOToSave);
        checkIfThisCarAlreadyInRepair(repairOrderToSave);
        checkIfThisCarHasUnfinishedRentOrder(repairOrderToSave);
        final RepairOrder createdRepairOrder = repairOrderRepository.save(repairOrderToSave);

        //создание DTO и отправка на второй сервис
        HttpDtoToAcceptRepair postDTOFromService1 = HttpDtoToAcceptRepair
                .create(createdRepairOrder.getId(), createdRepairOrder.getStartRepairDay());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<HttpDtoToAcceptRepair> request = new HttpEntity<>(postDTOFromService1, headers);
        ResponseEntity<HttpDtoToAcceptRepair> response = restTemplate
                .exchange(urlService2, HttpMethod.POST, request, HttpDtoToAcceptRepair.class);

        //обработка ответа со второго сервиса
        if (response.getStatusCode().value() == 200) {
            createdRepairOrder.setEndRepairDay(response.getBody().getDay());
            createdRepairOrder.setRepairIdExternal(response.getBody().getRowId());
            createdRepairOrder.setRepairStatus(RepairStatus.IN_PROGRESS);
            repairOrderRepository.save(createdRepairOrder);
        }

        final RepairOrderDTO createdRepairOrderDTO = repairOrderMapper.toRepairOrderDTO(createdRepairOrder);
        return ResponseEntity.ok().body(createdRepairOrderDTO);
    }

    @PutMapping("/repairOrders/{id}/finished")
    public ResponseEntity<RepairOrderDTO> finishStatusOfRepairOrder(@PathVariable(value = "id") Integer orderRepairId, @Valid @RequestBody HttpDtoToFinishRepair httpDtoFromService2)
            throws ResourceNotFoundException {
        RepairOrder repairOrder = repairOrderRepository.findById(orderRepairId)
                .orElseThrow(() -> new ResourceNotFoundException("Repair order not found with id: " + orderRepairId));
        if(httpDtoFromService2.getPrice() != null) {
            repairOrder.setPrice(httpDtoFromService2.getPrice());
            repairOrder.setRepairStatus(RepairStatus.FINISHED);
        }
        final RepairOrder updatedRepairOrder = repairOrderRepository.save(repairOrder);
        final RepairOrderDTO updatedrepairOrderDTO = repairOrderMapper.toRepairOrderDTO(repairOrder);
        return ResponseEntity.ok().body(updatedrepairOrderDTO);
    }

    @PutMapping("/repairOrders/{id}/update")
    public ResponseEntity<RepairOrderDTO> updateStatusOfRepairOrder(@PathVariable(value = "id") Integer orderRepairId)
            throws ResourceNotFoundException, BadRequestException {
        RepairOrder repairOrder = repairOrderRepository.findById(orderRepairId)
                .orElseThrow(() -> new ResourceNotFoundException("Repair order not found with id: " + orderRepairId));
        if(repairOrder.getRepairIdExternal() == null) {
            throw new BadRequestException("Server 2 have not accepted this repair order yet");
        }

        //получение DTO из второго сервиса и изменение параметров в своей таблице
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<HttpDtoToFinishRepair> entity = new HttpEntity<>(headers);
        ResponseEntity<HttpDtoToFinishRepair> httpDtoFromService2 = restTemplate
                .exchange(urlService2 + "/" + repairOrder.getRepairIdExternal(), HttpMethod.GET, entity, HttpDtoToFinishRepair.class);
        if(httpDtoFromService2.getStatusCode().value() == 200 && httpDtoFromService2.getBody().getPrice() != null) {
            repairOrder.setPrice(httpDtoFromService2.getBody().getPrice());
            repairOrder.setRepairStatus(RepairStatus.FINISHED);
        }
        final RepairOrder updatedRepairOrder = repairOrderRepository.save(repairOrder);
        final RepairOrderDTO updatedrepairOrderDTO = repairOrderMapper.toRepairOrderDTO(repairOrder);
        return ResponseEntity.ok().body(updatedrepairOrderDTO);
    }

    private void checkIfThisCarAlreadyInRepair(RepairOrder repairOrderToSave) throws BadRequestException, ResourceNotFoundException {
        //проверка на то, что машина уже сломалась и в сервисе
        Car car = carRepository.findById(repairOrderToSave.getCarId())
                .orElseThrow(() -> new ResourceNotFoundException("Car not found with id: " + repairOrderToSave.getCarId()));
        if (!car.getRepairOrders().isEmpty()) {
            for (RepairOrder repairOrder : car.getRepairOrders()) {
                if(!repairOrder.getRepairStatus().equals(RepairStatus.FINISHED)) {
                    throw new BadRequestException("This car is already in repair");
                }
            }
        }
    }

    private void checkIfThisCarHasUnfinishedRentOrder(RepairOrder repairOrderToSave) throws BadRequestException, ResourceNotFoundException {
        Car car = carRepository.findById(repairOrderToSave.getCarId())
                .orElseThrow(() -> new ResourceNotFoundException("Car not found with id: " + repairOrderToSave.getCarId()));
        //если заказ на аренду этой машины существует, то мы заканчиваем заказ датой поломки
        if(!car.getOrders().isEmpty() && car.getOrderIdThatIsUnavailable() != null) {
            Order order = orderRepository.findById(car.getOrderIdThatIsUnavailable())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + car.getOrderIdThatIsUnavailable()));
            if(repairOrderToSave.getStartRepairDay().before(order.getStartDay())) {
                throw new BadRequestException("Start day of repair cannot be earlier then start day of rent: " + new SimpleDateFormat("dd.MM.yyyy").format(order.getStartDay()));
            }
            order.setEndDay(repairOrderToSave.getStartRepairDay());
            repairOrderToSave.setCustomerId(order.getCustomerId());
        }
    }

}
