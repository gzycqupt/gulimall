package com.atguigu.gulimall.member.exception;

public class PhoneExistException extends RuntimeException{

    public PhoneExistException() {
        super("该电话号码已被注册");
    }
}
