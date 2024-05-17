package com.hyunn.capstone.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyunn.capstone.dto.request.KakaoPayReadyRequest;
import com.hyunn.capstone.dto.response.KakaoPayApproveResponse;
import com.hyunn.capstone.dto.response.KakaoPayReadyResponse;
import com.hyunn.capstone.entity.Image;
import com.hyunn.capstone.entity.Payment;
import com.hyunn.capstone.entity.User;
import com.hyunn.capstone.exception.ApiKeyNotValidException;
import com.hyunn.capstone.exception.ApiNotFoundException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.hyunn.capstone.exception.ImageNotFoundException;
import com.hyunn.capstone.exception.UserNotFoundException;
import com.hyunn.capstone.repository.ImageJpaRepository;
import com.hyunn.capstone.repository.PaymentJpaRepository;
import com.hyunn.capstone.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class KakaoPayService {

  @Value("${spring.security.x-api-key}")
  private String xApiKey;

  @Value("${spring.security.oauth2.client.kakaoPay.client-id}")
  private String cid;

  @Value("${spring.security.oauth2.client.kakaoPay.client-secret}")
  private String admin_Key;

  @Value("${spring.security.oauth2.client.kakaoPay.ready-uri}")
  private String readyUrl;

  @Value("${spring.security.oauth2.client.kakaoPay.approve-uri}")
  private String approveUrl;

  @Value("${spring.security.oauth2.client.kakaoPay.redirect_approval_url}")
  private String redirect_approval_url;

  @Value("${spring.security.oauth2.client.kakaoPay.redirect_fail_url}")
  private String redirect_fail_url;

  @Value("${spring.security.oauth2.client.kakaoPay.redirect_cancel_url}")
  private String redirect_cancel_url;

  @Value("${spring.security.oauth2.client.kakaoPay.partner_order_id}")
  private String partner_order_id;

  private KakaoPayReadyResponse kakaoPayReadyResponse = KakaoPayReadyResponse.create();

  private final ImageJpaRepository imageJpaRepository;

  private final UserJpaRepository userJpaRepository;

  private final PaymentJpaRepository paymentJpaRepository;

  /**
   * 카카오페이 결제준비 단계
   */
  public KakaoPayReadyResponse getReady(Long imageId, String phone, String apiKey,
      KakaoPayReadyRequest kakaoPayReadyRequest)
      throws JsonProcessingException {
    // API KEY 유효성 검사
    if (apiKey == null || !apiKey.equals(xApiKey)) {
      throw new ApiKeyNotValidException("API KEY가 올바르지 않습니다.");
    }

    Optional<Image> image = Optional.ofNullable(
        imageJpaRepository.findById(imageId)
            .orElseThrow(() -> new ImageNotFoundException("이미지를 가져오지 못했습니다.")));

    Optional<User> user = Optional.ofNullable(
        userJpaRepository.findUserByPhone(phone)
            .orElseThrow(() -> new UserNotFoundException("유저 정보를 가져오지 못했습니다.")));

    if (image.get().getUser().getUserId() != user.get().getUserId()) {
      throw new UserNotFoundException("해당 유저가 소유하고 있는 이미지가 아닙니다.");
    }

    String partner_user_id = kakaoPayReadyRequest.getPartner_user_id();
    // 요청 바디를 구성합니다.
    Map<String, Object> params = new HashMap<>();
    params.put("cid", "TC0ONETIME");
    params.put("partner_order_id", partner_order_id);
    params.put("partner_user_id", partner_user_id);
    params.put("item_name", kakaoPayReadyRequest.getItem_name());
    params.put("quantity", kakaoPayReadyRequest.getQuantity());
    params.put("total_amount", kakaoPayReadyRequest.getTotal_amount());
    params.put("tax_free_amount", 0);
    params.put("approval_url", redirect_approval_url);
    params.put("cancel_url", redirect_cancel_url);
    params.put("fail_url", redirect_fail_url);

    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "SECRET_KEY " + admin_Key);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String requestUrl = readyUrl;

    // HttpEntity를 생성합니다.
    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(params, headers);

    // API 호출을 수행합니다.
    ResponseEntity<String> response = restTemplate.exchange(
        requestUrl,
        HttpMethod.POST,
        requestEntity,
        String.class);

    // API 응답을 처리합니다.
    if (response.getStatusCode().is2xxSuccessful()) {
      // 성공적으로 API를 호출한 경우의 처리
      System.out.println("API 호출 성공: " + response.getBody());
    } else {
      // API 호출이 실패한 경우의 처리
      System.out.println("API 호출 실패: " + response.getStatusCode());
      throw new ApiNotFoundException("API 호출에 문제가 생겼습니다.");
    }

    // JSON 파싱
    String responseBody = response.getBody();
    if (responseBody == null || responseBody.isEmpty()) {
      throw new ApiNotFoundException("API 응답이 비어 있습니다.");
    }

    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseJson = mapper.readTree(responseBody);
    String tid = responseJson.get("tid").asText();
    String next_redirect_mobile_url = responseJson.get("next_redirect_mobile_url").asText();
    String next_redirect_pc_url = responseJson.get("next_redirect_pc_url").asText();

    kakaoPayReadyResponse.setNext_redirect_pc_url(next_redirect_pc_url);
    kakaoPayReadyResponse.setNext_redirect_mobile_url(next_redirect_mobile_url);
    kakaoPayReadyResponse.setTid(tid);
    kakaoPayReadyResponse.setPartner_user_id(partner_user_id);
    kakaoPayReadyResponse.setImageId(imageId);

    return kakaoPayReadyResponse;
  }


  /**
   * 카카오페이 결제승인 단계
   */
  public KakaoPayApproveResponse getApprove(String pgToken) throws JsonProcessingException {

    // 요청 헤더를 구성합니다.
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "SECRET_KEY " + admin_Key);
    headers.setContentType(MediaType.APPLICATION_JSON);

    // 요청 바디를 구성합니다.
    Map<String, Object> params = new HashMap<>();
    params.put("cid", "TC0ONETIME");
    params.put("tid", kakaoPayReadyResponse.getTid());
    params.put("partner_order_id", partner_order_id);
    params.put("partner_user_id", kakaoPayReadyResponse.getPartner_user_id());
    params.put("pg_token", pgToken);

    // HttpEntity를 생성합니다.
    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(params, headers);

    // API 호출을 수행합니다.
    ResponseEntity<String> response = restTemplate.exchange(
        approveUrl,
        HttpMethod.POST,
        requestEntity,
        String.class
    );

    // API 응답을 처리합니다.
    if (!response.getStatusCode().is2xxSuccessful()) {
      // API 호출이 실패한 경우의 처리
      throw new ApiNotFoundException("API 호출에 실패했습니다. 상태 코드: " + response.getStatusCode());
    }

    //JSON 파싱
    String responseBody = response.getBody();
    if (responseBody == null || responseBody.isEmpty()) {
      throw new ApiNotFoundException("API 응답이 비어 있습니다.");
    }

    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseJson = mapper.readTree(responseBody);
    KakaoPayApproveResponse.Amount amount = new KakaoPayApproveResponse.Amount();
    amount.setTotal(responseJson.get("amount").get("total").asInt());

    // KakaoPayApproveResponse 생성을 위한 나머지 데이터 추출
    String aid = responseJson.get("aid").asText();
    String payment_method_type = responseJson.get("payment_method_type").asText();
    String item_name = responseJson.get("item_name").asText();
    String item_code = responseJson.get("item_code").asText();
    Integer quantity = responseJson.get("quantity").asInt();

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    LocalDateTime created_at = LocalDateTime.parse(responseJson.get("created_at").asText(),
        formatter);
    LocalDateTime approved_at = LocalDateTime.parse(responseJson.get("approved_at").asText(),
        formatter);

    String Partner_user_id = kakaoPayReadyResponse.getPartner_user_id();

    Optional<User> user = Optional.ofNullable(
        userJpaRepository.findUserByPhone(Partner_user_id)
            .orElseThrow(() -> new UserNotFoundException("유저 정보를 가져오지 못했습니다.")));

    Optional<Image> image = Optional.ofNullable(
        imageJpaRepository.findById(kakaoPayReadyResponse.getImageId())
            .orElseThrow(() -> new ImageNotFoundException("이미지 정보를 가져오지 못했습니다.")));

    Payment payment = Payment.createPayment(item_name, amount.getTotal().intValue(),
        user.get().getAddress(), "결제 완료", image.get());
    paymentJpaRepository.save(payment);

    Image existImage = image.get();
    existImage.connectPayment(payment);
    imageJpaRepository.save(existImage);

    return KakaoPayApproveResponse.create(
        aid, kakaoPayReadyResponse.getTid(), cid, payment_method_type, Partner_user_id,
        amount, item_name, item_code, quantity,
        created_at, approved_at
    );
  }

}