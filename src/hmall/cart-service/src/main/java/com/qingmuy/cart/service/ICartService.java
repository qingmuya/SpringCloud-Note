package com.qingmuy.cart.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmuy.cart.domain.dto.CartFormDTO;
import com.qingmuy.cart.domain.po.Cart;
import com.qingmuy.cart.domain.vo.CartVO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 订单详情表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface ICartService extends IService<Cart> {

    void addItem2Cart(CartFormDTO cartFormDTO);

    List<CartVO> queryMyCarts();

    void removeByItemIds(Collection<Long> itemIds);

    void removeCartByItemIds(Set<Long> itemIds, Long UserId);
}
