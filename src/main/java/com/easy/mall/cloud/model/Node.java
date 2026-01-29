package com.easy.mall.cloud.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Node {
  private String name;
  private Node next;

  public Node(String name) {
    this.name = name;
  }
}