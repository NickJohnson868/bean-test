package com.easy.mall.cloud.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Source {
  private String name;
  private int age;

  public Source(String name, int age) {
    this.name = name;
    this.age = age;
  }
}