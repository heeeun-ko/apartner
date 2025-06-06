package com.ohammer.apartner.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "아이디 찾기 응답 DTO")
public class FindUserNameResponse {
    @Schema(description = "사용자 아이디 반환")
    private String userName;
}
