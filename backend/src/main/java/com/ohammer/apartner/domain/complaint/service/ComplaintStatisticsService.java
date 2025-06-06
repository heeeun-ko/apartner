package com.ohammer.apartner.domain.complaint.service;

import com.ohammer.apartner.domain.complaint.dto.response.ComplaintCountByStatusResponseDto;
import com.ohammer.apartner.domain.complaint.dto.response.ComplaintHandlingRateResponseDto;
import com.ohammer.apartner.domain.complaint.dto.response.ComplaintIncreaseRateResponseDto;
import com.ohammer.apartner.domain.complaint.repository.ComplaintRepository;
import com.ohammer.apartner.domain.user.entity.Role;
import com.ohammer.apartner.domain.user.entity.User;
import com.ohammer.apartner.security.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintStatisticsService {


    private final ComplaintRepository complaintRepository;

    public Long getTodayTotalComplaintCount() throws AccessDeniedException {

        User user = SecurityUtil.getCurrentUser();

        if (user == null) {
            throw new AccessDeniedException("로그인되지 않은 사용자입니다.");
        }

        Set<Role> userRoles = user.getRoles();

        boolean hasRequiredRole = userRoles.stream()
                .anyMatch(role -> role == Role.ADMIN);

        if (!hasRequiredRole) {
            throw new AccessDeniedException("통계를 확인할 권한이 없습니다.");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        // 새로 추가한 메서드를 호출하여 전체 개수 가져오기
        Long totalCount = complaintRepository.countTotalComplaintsToday(startOfDay, endOfDay);

        return totalCount; // 전체 개수 반환
    }

    public List<ComplaintCountByStatusResponseDto> getComplainsGroupByStatus() throws AccessDeniedException {
        User user = SecurityUtil.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("로그인되지 않은 사용자입니다.");
        }

        Set<Role> userRoles = user.getRoles();

        boolean hasRequiredRole = userRoles.stream()
                .anyMatch(role -> role == Role.ADMIN);

        if (!hasRequiredRole) {
            throw new AccessDeniedException("통계를 확인할 권한이 없습니다.");
        }

        // 상태별 개수 조회
        List<Object[]> results = complaintRepository.countComplaintsGroupByStatus();

        // DTO로 변환
        return results.stream()
                .map(result -> ComplaintCountByStatusResponseDto.builder()
                        .status(result[0].toString()) // Enum.name()
                        .count((Long) result[1])
                        .build())
                .collect(Collectors.toList());
    }

    public ComplaintHandlingRateResponseDto getComplaintHandlingRate() throws AccessDeniedException {
        User user = SecurityUtil.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("로그인되지 않은 사용자입니다.");
        }

        Set<Role> userRoles = user.getRoles();
        boolean hasRequiredRole = userRoles.stream()
                .anyMatch(role -> role == Role.ADMIN);

        if (!hasRequiredRole) {
            throw new AccessDeniedException("통계를 확인할 권한이 없습니다.");
        }

        Long totalCount = complaintRepository.countAllComplaints();
        Long handledCount = complaintRepository.countHandledComplaints();

        double handlingRate = 0.0;
        if (totalCount != 0) {
            handlingRate = (double) handledCount / totalCount * 100;
        }

        return ComplaintHandlingRateResponseDto.builder()
                .totalCount(totalCount)
                .handledCount(handledCount)
                .handlingRate(Math.round(handlingRate * 10.0) / 10.0) // 소수점 1자리
                .build();
    }

    // 어제 민원 수를 가져오는 메서드 추가
    private Long getYesterdayTotalComplaintCount() throws AccessDeniedException {
         User user = SecurityUtil.getCurrentUser();
         if (user == null || !user.getRoles().stream().anyMatch(role -> role == Role.ADMIN)) {
             throw new AccessDeniedException("권한이 없습니다.");
         }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfYesterday = yesterday.atStartOfDay();
        LocalDateTime endOfYesterday = yesterday.plusDays(1).atStartOfDay();

        return complaintRepository.countByCreatedAtBetween(startOfYesterday, endOfYesterday);
    }

    public ComplaintIncreaseRateResponseDto getComplaintIncreaseRateFromYesterday() throws AccessDeniedException {
        User user = SecurityUtil.getCurrentUser();
        if (user == null || !user.getRoles().stream().anyMatch(role -> role == Role.ADMIN)) {
            throw new AccessDeniedException("통계를 확인할 권한이 없습니다.");
        }

        Long todayCount = getTodayTotalComplaintCount(); // 내부 메서드 호출 (권한 체크 포함)

        Long yesterdayCount = getYesterdayTotalComplaintCount(); // 내부 메서드 호출

        double increaseRate = 0.0;

        if (yesterdayCount == 0) {
            if (todayCount > 0) {
                increaseRate = todayCount > 0 ? 100.0 : 0.0;
            } else {
                increaseRate = 0.0;
            }
        } else {
            increaseRate = ((double) (todayCount - yesterdayCount) / yesterdayCount) * 100.0;
        }

        increaseRate = Math.round(increaseRate * 10.0) / 10.0;

        return ComplaintIncreaseRateResponseDto.builder()
                .todayCount(todayCount)
                .yesterdayCount(yesterdayCount)
                .increaseRate(increaseRate)
                .build();
    }

}
