package com.hyunn.capstone.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ThreeDimensionRequest {

  @Schema(type = "String", description = "이미지", example = "human_face.img")
  @NotBlank(message = "이미지를 입력해주세요.")
  private String image;

  @Schema(type = "String", description = "성별", example = "female")
  @NotBlank(message = "성별을 입력해주세요.")
  private String gender;

  @Schema(type = "String", description = "감정", example = "happy")
  @NotBlank(message = "감정을 입력해주세요.")
  private String emotion;

  public ThreeDimensionRequest(String image, String gender, String emotion) {
    this.image = image;
    this.gender = gender;
    this.emotion = emotion;
  }

  public static ThreeDimensionRequest create(String image, String gender, String emotion) {
    return new ThreeDimensionRequest(image, gender, emotion);
  }
}