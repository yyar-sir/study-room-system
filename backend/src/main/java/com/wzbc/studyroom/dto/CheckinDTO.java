package com.wzbc.studyroom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 扫码签到入参：二维码里携带的签到码 */
@Data
public class CheckinDTO {

    @NotBlank(message = "签到码不能为空")
    private String checkinCode;
}
