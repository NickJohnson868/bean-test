package com.easy.mall.cloud.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ComplexSource {
  private String id;
  private Node node;

  public ComplexSource(String id, Node node) {
    this.id = id;
    this.node = node;
  }
}