package com.rytech.controller;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: YuBin-002726
 * @Date: 2019/8/26 16:56
 */
@RestController
public class MyController {

    @HystrixCommand
    @GetMapping("/")
    public String demo() throws InterruptedException {
        Thread.sleep(500);
        return "SUCCESS";
    }


}
