package io.github.xianspiderman.iotops.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public record PageResult<T>(long page, long size, long total, List<T> records) {
    public static <T> PageResult<T> from(IPage<T> source) {
        return new PageResult<>(source.getCurrent(), source.getSize(), source.getTotal(), source.getRecords());
    }
}

