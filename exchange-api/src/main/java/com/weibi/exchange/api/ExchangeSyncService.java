package com.weibi.exchange.api;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author jsong
 * @version 1.0
 * @date 2020-06-13 19:06
 */
@ApiOperation("撮合异步数据处理api")
@FeignClient(name = "${feign.exchange-sync}", path = "exchange-sync")
public interface ExchangeSyncService {

    @GetMapping("/selectOne")
    @ApiOperation(value = "id查询", notes = "id查询")
    Object selectOne(@ApiParam(value = "id") @RequestParam String id);

}
