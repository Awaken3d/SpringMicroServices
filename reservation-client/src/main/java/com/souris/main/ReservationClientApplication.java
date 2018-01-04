package com.souris.main;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.MessageChannel;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;


interface ReservationClientChannels {

	@Output
	MessageChannel output();
}

@EnableCircuitBreaker
@EnableBinding(ReservationChannels.class)
@EnableZuulProxy
@EnableDiscoveryClient
@SpringBootApplication
public class ReservationClientApplication {
	@LoadBalanced
	@Bean
	public RestTemplate restTemplate() {
	    return new RestTemplate();
	}
	public static void main(String[] args) {
		SpringApplication.run(ReservationClientApplication.class, args);
	}
}

@MessagingGateway
interface ReservationWriter {

    @Gateway(requestChannel = "output")
    void write(String rn);
}

interface ReservationChannels {

    @Output
    MessageChannel output();
}

@RestController
@RequestMapping("/reservations")
class ReservationGatewayRestController {
	
	@Autowired
	private RestTemplate restTemplate;
	
	private final ReservationWriter reservationWriter;

	@Autowired
    public ReservationGatewayRestController(ReservationWriter reservationWriter) {
       
        this.reservationWriter = reservationWriter;
    }
	public Collection<String> getReservationNamesFallback() {
		return new ArrayList<>();
	}
	
	@RequestMapping(method = RequestMethod.POST)
	public void writeReservation(@RequestBody Reservation r) {
		this.reservationWriter.write(r.getReservationName());
	}
	
	@HystrixCommand(fallbackMethod = "getReservationNamesFallback")
	@RequestMapping(method = RequestMethod.GET, value = "/names")
	public Collection<String> getReservationNames() {
		ParameterizedTypeReference<Resources<Reservation>> ptr = new ParameterizedTypeReference<Resources<Reservation>>() {};
		ResponseEntity<Resources<Reservation>> entity =  this.restTemplate.exchange("http://reservation-service/reservations", HttpMethod.GET, null, ptr);
		return entity.getBody().getContent().stream().map(Reservation :: getReservationName).collect(Collectors.toList());
	}
}

class Reservation {
	private String reservationName;

	public String getReservationName() {
		return reservationName;
	}


}
