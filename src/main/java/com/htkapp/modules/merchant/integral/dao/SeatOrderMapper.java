package com.htkapp.modules.merchant.integral.dao;

import com.htkapp.modules.API.dto.SeatOrderDetail;
import com.htkapp.modules.merchant.integral.entity.SeatOrder;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订座接口
 */
public interface SeatOrderMapper {

    /* ========================接口开始======================== */
    //根据用户token查找用户下的订座订单列表和店铺信息
    List<SeatOrder> getSeatOrderListByTokenAndShopIdDAO(@Param("token") String token, @Param("shopId") int shopId, @Param("orderBy") String orderBy);
    //通过订单号查询订单详情
    SeatOrder getSeatOrderByOrderNumberDAO(String orderNumber);
    //插入座位预定
    int insertSeatOrderByTokenDAO(SeatOrder order);
    //根据订座订单号查询订座详情
    SeatOrderDetail getSeatOrderDetailDAO(String orderNumber, String token);
    /* ========================接口结束======================== */


    /* =====================JSP接口开始========================= */
    //插入座位订单
    int insertSeatOrderData(SeatOrder seatOrder);
    /* =====================JSP接口结束========================= */
}