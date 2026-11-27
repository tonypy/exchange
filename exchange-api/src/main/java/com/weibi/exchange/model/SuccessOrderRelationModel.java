package com.weibi.exchange.model;

import lombok.Data;

import java.util.List;

@Data
public class SuccessOrderRelationModel {

    private String userId;

    private String orderNo;

    private Boolean isBid;

    private List<String> successOrderNos;

}
