package com.easy.mall.cloud.model;

import java.util.List;

public record UserRecord(String name, int level, List<String> roles) {
}