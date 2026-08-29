package com.example.leaseplatform.usr.service;

import com.example.leaseplatform.common.BizException;
import com.example.leaseplatform.common.TimeUtil;
import com.example.leaseplatform.security.UserContext;
import com.example.leaseplatform.usr.dto.MeUpdateReq;
import com.example.leaseplatform.usr.dto.MeVO;
import com.example.leaseplatform.usr.entity.UsrUser;
import com.example.leaseplatform.usr.mapper.UsrUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户自身服务（GET/PUT /api/v1/me）：个人资料 + 余额 + 会员等级。
 */
@Service
@RequiredArgsConstructor
public class UsrUserService {

    private final UsrUserMapper userMapper;

    /** 我的资料 */
    public MeVO me() {
        return toVO(requireActive(UserContext.getUserId()));
    }

    /** 更新我的资料（昵称 / 头像 / 手机号） */
    public MeVO updateMe(MeUpdateReq req) {
        UsrUser user = requireActive(UserContext.getUserId());
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(req.getAvatarUrl());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        userMapper.updateById(user);
        return toVO(user);
    }

    private UsrUser requireActive(Long id) {
        UsrUser user = userMapper.selectById(id);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.unauthorized("账号不存在或已被禁用");
        }
        return user;
    }

    private MeVO toVO(UsrUser user) {
        MeVO vo = new MeVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setUserType(user.getUserType());
        vo.setEnterpriseId(user.getEnterpriseId());
        vo.setMemberLevel(user.getMemberLevel());
        vo.setIsEnterpriseAdmin(user.getIsEnterpriseAdmin());
        vo.setBalance(user.getBalance());
        vo.setGiftBalance(user.getGiftBalance());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(TimeUtil.toEpochMillis(user.getLastLoginAt()));
        vo.setCreatedAt(TimeUtil.toEpochMillis(user.getCreatedAt()));
        return vo;
    }
}
