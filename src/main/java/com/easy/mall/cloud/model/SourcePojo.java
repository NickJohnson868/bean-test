package com.easy.mall.cloud.model;

import lombok.Data;

@Data
public class SourcePojo {
  private String name;
  private int age;
  private String address;
  private double score;

  public SourcePojo(String name, int age, String address, double score) {
    this.name = name;
    this.age = age;
    this.address = address;
    this.score = score;
  }
}