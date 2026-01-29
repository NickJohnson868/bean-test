package com.easy.mall.cloud.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WrapperSource {
  private Integer age;
  private Boolean active;

  public WrapperSource(Integer age, Boolean active) {
    this.age = age;
    this.active = active;
  }
}