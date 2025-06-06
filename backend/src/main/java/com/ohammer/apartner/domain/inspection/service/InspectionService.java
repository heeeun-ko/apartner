package com.ohammer.apartner.domain.inspection.service;

import com.ohammer.apartner.domain.inspection.dto.InspectionIssueDto;
import com.ohammer.apartner.domain.inspection.dto.InspectionRequestDto;
import com.ohammer.apartner.domain.inspection.dto.InspectionResponseDetailDto;
import com.ohammer.apartner.domain.inspection.dto.InspectionUpdateDto;
import com.ohammer.apartner.domain.inspection.entity.Inspection;
import com.ohammer.apartner.domain.inspection.entity.InspectionType;
import com.ohammer.apartner.domain.inspection.entity.Result;
import com.ohammer.apartner.domain.inspection.repository.InspectionRepository;
import com.ohammer.apartner.domain.inspection.repository.InspectionTypeRepository;
import com.ohammer.apartner.domain.notice.dto.request.NoticeRequestDto;
import com.ohammer.apartner.domain.notice.service.NoticeServiceImpl;
import com.ohammer.apartner.domain.user.entity.User;
import com.ohammer.apartner.domain.user.repository.UserRepository;
import com.ohammer.apartner.global.Status;
import com.ohammer.apartner.security.utils.SecurityUtil;
import com.ohammer.apartner.global.service.AlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class InspectionService {
    private final InspectionRepository inspectionRepository;
    private final InspectionTypeRepository inspectionTypeRepository;
    private final AlarmService alarmService;
    private final NoticeServiceImpl noticeService;
    //대충 유저 리포지토리가 있다고 가정
    public boolean itIsYou(Inspection inspection) {
        User user = SecurityUtil.getCurrentUser();
        if (user == null)
            return false;
        return user.getUserName().equals(inspection.getUser().getUserName());
    }

    public Result findResult(String result) {
        switch (result) {
            case "CHECKED":
                return Result.CHECKED;
            case "PENDING":
                return Result.PENDING;
            case "ISSUE":
                return Result.ISSUE;
            case"NOTYET":
                return Result.NOTYET;
        }
        return null;
    }

    @Transactional
    public Inspection newInspectionSchedule (InspectionRequestDto dto) {
        User user = SecurityUtil.getCurrentUser();
        if (user == null)
            throw new RuntimeException("인증 과정에 오류가 생겼음");

        InspectionType type = inspectionTypeRepository.findByTypeName(dto.getType());
        Result result;
        if (dto.getStartAt().isAfter(LocalDateTime.now()))
            result = Result.NOTYET;
        else
            result = Result.PENDING;

        Inspection inspection = Inspection.builder()
                .user(user)
                .startAt(dto.getStartAt())
                .finishAt(dto.getFinishAt())
                .detail(dto.getDetail())
                .title(dto.getTitle())
                .type(type)
                .result(result)
                .modifiedAt(null)
                .createdAt(LocalDateTime.now())
                .status(Status.ACTIVE)
                .build();
        Inspection saved = inspectionRepository.save(inspection);

        NoticeRequestDto noticeRequestDto = new NoticeRequestDto(dto.getTitle(), dto.getDetail(), null, null, null);

        noticeService.createNotice(noticeRequestDto, user.getId());

        
        if (user.getApartment() != null) {
            String message = user.getUserName() + "님이 새로운 점검 일정을 등록했습니다: " + dto.getTitle();
            alarmService.notifyUser(
                user.getId(),
                user.getApartment().getId(),
                "새 점검 일정",
                "info",
                "INSPECTION_NEW",
                message,
                null,
                user.getId(),
                saved.getId(),
                null
            );
        }
        
        return saved;
    }

    //전부 조회
    public Page<InspectionResponseDetailDto> showAllInspections(Pageable pageable) {
        return inspectionRepository.findAllByStatusNotWithdrawn(Status.WITHDRAWN, pageable)
                 .map(InspectionResponseDetailDto::fromEntity);
    }

    //수정, 결과입력?
    //여기에 리턴이 필요할지도
    @Transactional
    public void updateInspection(Long id, InspectionUpdateDto dto) {
        Inspection inspection = inspectionRepository.findById(id).orElseThrow();
        if (!itIsYou(inspection))
            throw new RuntimeException("본인만 수정 가능합니다");

        inspection.setDetail(dto.getDetail());
        inspection.setStartAt(dto.getStartAt());
        inspection.setFinishAt(dto.getFinishAt());
        inspection.setModifiedAt(LocalDateTime.now());
        inspection.setTitle(dto.getTitle());

        inspection.setType(inspectionTypeRepository.findByTypeName(dto.getType()));

        inspection.setResult(findResult(dto.getResult()));

        if (dto.getResult().equals("CHECKED"))
            inspection.setFinishAt(LocalDateTime.now());
        else if (dto.getResult().equals("PENDING"))
            inspection.setStartAt(LocalDateTime.now());

        inspectionRepository.save(inspection);
        User user = inspection.getUser();
        if (user.getApartment() != null) {
            String message = user.getUserName() + "님이 점검 일정을 수정했습니다: " + dto.getTitle();
            alarmService.notifyUser(
                user.getId(),
                user.getApartment().getId(),
                "점검 일정 수정",
                "info",
                "INSPECTION_UPDATE",
                message,
                null,
                user.getId(),
                inspection.getId(),
                null
            );
        }
    }

    //삭제
    @Transactional
    public void deleteInspection(Long id) {
        Inspection inspection = inspectionRepository.findById(id).orElseThrow();
        if(!itIsYou(inspection))
            throw new RuntimeException("본인이 아닌뎁쇼");

        inspection.setStatus(Status.WITHDRAWN);
        inspectionRepository.save(inspection);
        
        User user = inspection.getUser();
        if (user.getApartment() != null) {
            String message = user.getUserName() + "님이 점검 일정을 삭제했습니다: " + inspection.getTitle();
            alarmService.notifyUser(
                user.getId(),
                user.getApartment().getId(),
                "점검 일정 삭제",
                "info",
                "INSPECTION_DELETE",
                message,
                null,
                user.getId(),
                inspection.getId(),
                null
            );
        }
    }


    //자료 자세히 보기
    @Transactional(readOnly = true)
    public InspectionResponseDetailDto showInspection(Long id) {

        Inspection inspection = inspectionRepository.findById(id).orElseThrow();
        User user = inspection.getUser();

        return new InspectionResponseDetailDto(inspection.getId(),
                user.getId(),
                user.getUserName(),
                inspection.getStartAt(),
                inspection.getFinishAt(),
                inspection.getTitle(),
                inspection.getDetail(),
                inspection.getResult(),
                inspection.getType().getTypeName());
    }


    //결과 수정
    @Transactional
    public void changeInspectionResult(Long id, String result) {
        if (!inspectionRepository.existsById(id))
            throw new RuntimeException("그거 없는댑쇼");
        Inspection inspection = inspectionRepository.findById(id).get();
        if (!itIsYou(inspection))
            throw new RuntimeException("본인만 완료 가능합니다");

        inspection.setResult(findResult(result));

        if (result.equals("CHECKED"))
            inspection.setFinishAt(LocalDateTime.now());
        else if (result.equals("PENDING"))
            inspection.setStartAt(LocalDateTime.now());


        inspection.setModifiedAt(LocalDateTime.now());


        inspectionRepository.save(inspection);
        
        User user = inspection.getUser();
        if (user.getApartment() != null) {
            String message = user.getUserName() + "님이 점검을 완료했습니다: " + inspection.getTitle();
            alarmService.notifyUser(
                user.getId(),
                user.getApartment().getId(),
                "점검 완료",
                "success",
                "INSPECTION_COMPLETE",
                message,
                null,
                user.getId(),
                inspection.getId(),
                null
            );
        }
    }

    //검색
    public Page<InspectionResponseDetailDto> searchInspection(String keyword, Pageable pageable) {
        return inspectionRepository
                .findActiveByTitleOrDetailContainingIgnoreCase(keyword, pageable)
                .map(InspectionResponseDetailDto::fromEntity);
    }


    // =========== 여기서 부턴 타입쪽 ===========

    //죄다 조회
    public List<InspectionType> showAllTypes() {
        return inspectionTypeRepository.findAll();
    }

    //추가
    @Transactional
    public InspectionType addType(String name) {
        if (inspectionTypeRepository.findByTypeName(name) != null)
            throw new RuntimeException("찾으려는 타입이 중복인뎁쇼");
        InspectionType inspectionType = InspectionType.builder()
                .typeName(name)
                .build();

        return inspectionTypeRepository.save(inspectionType);
    }

}
