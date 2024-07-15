package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页的数据结构
 */
@Data
public class ScrollResult {

    /**
     * 返回的数据
     */
    private List<?> list;

    /**
     * 最小的时间
     */
    private Long minTime;

    /**
     * 偏移量
     */
    private Integer offset;
}
