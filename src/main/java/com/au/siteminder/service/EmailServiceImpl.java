package com.au.siteminder.service;

import com.au.siteminder.framework.constant.SiteminderEmailServiceConstant;
import com.au.siteminder.framework.exception.SiteminderServicesException;
import com.au.siteminder.model.EmailRequest;
import com.au.siteminder.model.EmailResponse;
import com.au.siteminder.model.sendgrid.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private Environment environment;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    public EmailResponse sendEmail(EmailRequest emailRequest) {
        return sendMailViaSendGrid(emailRequest);
    }

    private EmailResponse sendMailViaSendGrid(EmailRequest emailRequest) {

        SendGridRequest sendGridRequest = new SendGridRequest();
        Content content = new Content("text/plain", emailRequest.getText());
        sendGridRequest.setContent(Collections.singletonList(content));
        sendGridRequest.setSubject(emailRequest.getSubject());
        From from = new From(emailRequest.getFrom());
        sendGridRequest.setFrom(from);

        List<To> toList = emailRequest.getTo().stream().map(to -> new To(to)).collect(Collectors.toList());
        List<CC> ccList = emailRequest.getCc().stream().map(cc -> new CC(cc)).collect(Collectors.toList());
        List<BCC> bccList = emailRequest.getBcc().stream().map(bcc -> new BCC(bcc)).collect(Collectors.toList());

        Personalization personalization = new Personalization();
        personalization.setTo(toList);
        personalization.setCc(ccList);
        personalization.setBcc(bccList);

        List<Personalization> personalizations = new ArrayList<>();
        personalizations.add(personalization);

        sendGridRequest.setPersonalizations(personalizations);

        RestTemplate restTemplate = restTemplateBuilder.build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String requestJson = null;

        try {
            requestJson = objectMapper.writeValueAsString(sendGridRequest);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + environment.getProperty(SiteminderEmailServiceConstant.SENDGRID_KEY_PROPERTY));

        HttpEntity<String> entity = new HttpEntity<String>(requestJson, headers);
        restTemplate.postForLocation(environment.getProperty(SiteminderEmailServiceConstant.SENDGRID_URI_PROPERTY), entity);

        return null;
    }

    private EmailResponse sendMailViaMailgun(EmailRequest emailRequest) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        restTemplate.getInterceptors().add(
                new BasicAuthorizationInterceptor(environment.getProperty(SiteminderEmailServiceConstant.MAILGUN_USER_PROPERTY),
                        environment.getProperty(SiteminderEmailServiceConstant.MAILGUN_KEY_PROPERTY)));
        restTemplate.getInterceptors().add((request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add(EmailRequest.FROM, emailRequest.getFrom());
        if (CollectionUtils.isNotEmpty(emailRequest.getTo())) {
            map.put(EmailRequest.TO, emailRequest.getTo());
        }
        if (CollectionUtils.isNotEmpty(emailRequest.getCc())) {
            map.put(EmailRequest.CC, emailRequest.getCc());
        }
        if (CollectionUtils.isNotEmpty(emailRequest.getBcc())) {
            map.put(EmailRequest.BCC, emailRequest.getBcc());
        }
        map.add(EmailRequest.SUBJECT, emailRequest.getSubject());
        map.add(EmailRequest.TEXT, emailRequest.getText());
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(map, headers);

        ResponseEntity<EmailResponse> response = null;
        try {
            response = restTemplate.postForEntity(environment.getProperty(SiteminderEmailServiceConstant.MAILGUN_URI_PROPERTY), request, EmailResponse.class);
        } catch (Exception e) {
            throw new SiteminderServicesException(e);
        }

        return response.getBody();
    }

}
