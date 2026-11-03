package com.dgcz.travel.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料更新请求
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 30, message = "昵称长度不能超过 30 个字符")
    private String nickname;

    /** 头像地址（可为上传返回的地址，也可为系统预设头像） */
    @Size(max = 500, message = "头像地址过长")
    private String avatar;
}
