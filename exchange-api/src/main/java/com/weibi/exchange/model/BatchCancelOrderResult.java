package com.weibi.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量撤单返回状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchCancelOrderResult {
    private String id;
    private Integer status;
    private String msg;
}
