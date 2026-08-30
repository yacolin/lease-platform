package com.example.leaseplatform.trd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.leaseplatform.trd.entity.TrdBalanceTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 余额流水表 Mapper。
 */
@Mapper
public interface TrdBalanceTransactionMapper extends BaseMapper<TrdBalanceTransaction> {
}
