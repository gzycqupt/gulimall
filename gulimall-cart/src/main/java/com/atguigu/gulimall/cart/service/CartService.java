package com.atguigu.gulimall.cart.service;

import com.atguigu.gulimall.cart.vo.Cart;
import com.atguigu.gulimall.cart.vo.CartItem;

import java.util.List;

public interface CartService {
    //将商品添加到购物车
    CartItem addToCart(Long skuId, Integer num);

    //获取购物车中某个购物项
    CartItem getCartItem(Long skuId);

    Cart getCart();

    public void clearCart(String cartKey);

    void checkItem(Long skuId, Integer check);

    void changeItemCount(Long skuId, Integer num);

    void deleteItem(Long skuId);

    List<CartItem> getUserCartItems();
}
