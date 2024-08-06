package com.qingmuy.trade.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.UserContext;
import com.qingmuy.api.client.CartClient;
import com.qingmuy.api.client.ItemClient;
import com.qingmuy.api.domain.dto.ItemDTO;
import com.qingmuy.api.domain.dto.OrderDetailDTO;
import com.qingmuy.api.domain.dto.OrderFormDTO;
import com.qingmuy.trade.domain.po.Order;
import com.qingmuy.trade.domain.po.OrderDetail;
import com.qingmuy.trade.mapper.OrderMapper;
import com.qingmuy.trade.service.IOrderDetailService;
import com.qingmuy.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final RabbitTemplate rabbitTemplate;

    private final IOrderDetailService detailService;

    private final ItemClient itemClient;

    private final CartClient cartClient;

    /**
     * 创建订单
     * @param orderFormDTO 订单信息
     * @return 订单ID
     */
    @Override
    @Transactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.清理购物车商品
        // cartClient.deleteCartItemByIds(itemIds);
        // TODO： 将下单具体商品、当前用户id发送到cart.clear.queue队列
        // 将购物车商品id与当前用户id封装在一起
        Map<String, Object> msg = new HashMap<>();
        msg.put("itemIds", itemIds);
        msg.put("userId", UserContext.getUser());

        try {
            rabbitTemplate.convertAndSend("trade.topic", "order.create", msg);
        } catch (Exception e) {
            log.error("订单创建的消息发送失败", e);
        }

        // 4.扣减库存
        try {
            itemClient.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }
        return order.getId();
    }

    /**
     * 将订单标注为支付成功
     * @param orderId
     */
    @Override
    public void markOrderPaySuccess(Long orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        updateById(order);
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
