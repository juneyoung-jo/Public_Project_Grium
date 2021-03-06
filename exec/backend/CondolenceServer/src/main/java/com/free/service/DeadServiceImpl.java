package com.free.service;


import com.free.entity.Dead;
import com.free.entity.key.CondolenceId;
import com.free.payload.request.dead.DeadInfoRequest;
import com.free.payload.request.dead.DeadPortraitRequest;
import com.free.payload.request.dead.DeadStatusRequest;
import com.free.payload.request.user.FamilyRelation;
import com.free.payload.request.user.UserAllInfoRequest;
import com.free.payload.request.user.UserInfo;
import com.free.payload.response.dead.*;
import com.free.repository.DeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DeadServiceImpl implements DeadService {

    private final DeadRepository deadRepository;

    private final RestTemplateBuilder restTemplateBuilder;

    private final CondolenceService condolenceService;

    static final String url = "http://k4c104.p.ssafy.io:8443";

    @Override
    public DeadAllInfo getDeadAll(String deadId) {

        return deadRepository.findByDeadId(UUID.fromString(deadId)).map(dead -> DeadAllInfo.builder()
                .deadId(dead.getDeadId())
                .userId(dead.getUserId())
                .imprintDate(dead.getImprintDate())
                .deceasedDate(dead.getDeceasedDate())
                .isPublic(dead.isPublic())
                .progressCheck(dead.isProgressCheck())
                .murmurName(dead.getMurmurName())
                .murmurAddress(dead.getMurmurAddress())
                .murmurLat(dead.getMurmurLat())
                .murmurLng(dead.getMurmurLng())
                .cemeteryName(dead.getCemeteryName())
                .cemeteryAddress(dead.getCemeteryAddress())
                .cemeteryLat(dead.getCemeteryLat())
                .cemeteryLng(dead.getCemeteryLng())
                .build()).orElse(null);
    }

    @Override
    public DeadInfo getDeadInfo(String deadId, String userId) {

        RestTemplate restTemplate = restTemplateBuilder.build();
        UserAllInfoRequest userAllInfoRequest = restTemplate.getForObject(url + "/user/allinfo?uid=" + deadId, UserAllInfoRequest.class);

        // condolence ??????
        CondolenceId condolenceId = CondolenceId.builder()
                .userId(UUID.fromString(userId))
                .deadId(UUID.fromString(deadId))
                .build();


        // ????????? ?????? ????????? ????????? ??????
        boolean flag = Stream.concat(userAllInfoRequest.getFamilyRelationList().stream(), userAllInfoRequest.getFriendRelationList().stream())
                .anyMatch(relation -> relation.getConcernUid().equals(userId));

        return deadRepository.findByDeadId(UUID.fromString(deadId))
                .map(dead -> DeadInfo.builder()
                        .deadId(dead.getDeadId())
                        .name(userAllInfoRequest.getUserInfo().getName())
                        .imprintDate(dead.getImprintDate())
                        .deceasedDate(dead.getDeceasedDate())
                        .progressCheck(dead.isProgressCheck())
                        .murmurName(dead.getMurmurName())
                        .murmurAddress(dead.getMurmurAddress())
                        .murmurLat(dead.getMurmurLat())
                        .murmurLng(dead.getMurmurLng())
                        .cemeteryName(dead.getCemeteryName())
                        .cemeteryAddress(dead.getCemeteryAddress())
                        .cemeteryLat(dead.getCemeteryLat())
                        .cemeteryLng(dead.getCemeteryLng())
                        .condolenceCount(condolenceService.getCondolenceCount(UUID.fromString(deadId)))
                        .condolenceCheck(condolenceService.checkCondolence(condolenceId)) // ???????????? ???????????? ??????
                        .relationCheck(flag)
                        .familyRelations(userAllInfoRequest.getFamilyRelationList())
                        .birthday(userAllInfoRequest.getUserInfo().getBirthday())
                        .birthyear(userAllInfoRequest.getUserInfo().getBirthyear())
                        .commentForCondolence(userAllInfoRequest.getUserInfo().getCommentForCondolence())
                        .userImagesCount(userAllInfoRequest.getImageSize()) // user_Image size ???????????? Resttemplate
                        .portraitUrl(dead.getPortraitUrl()) // ????????????
                        .isPublic(dead.isPublic())
                        .build()).orElse(null);
    }

    @Override
    public UserInfo[] getPublicDeadList(String userId) {
        // 1.  Dead ??????????????? is_public ??? true??? ????????? ??? ????????????
//        Pageable pageable = PageRequest.of(page, 5);

        List<Dead> allByIsPublic = deadRepository.findAllByIsPublicAndUserIdIsNotNull(true);

        Map<String, Dead> deadMap = allByIsPublic.stream()
                .collect(Collectors.toMap(
                        dead -> dead.getDeadId().toString(),
                        dead -> dead
                ));

        //auth api ??????
        UserInfo[] userInfos = restTemplateBuilder.build()
                .postForObject(url + "/user/list", deadMap.keySet(), UserInfo[].class);

        for (UserInfo userInfo : userInfos) {
            userInfo.setImageUrl(deadMap.get(userInfo.getId()).getPortraitUrl());
            userInfo.setProgressCheck(deadMap.get(userInfo.getId()).isProgressCheck());
        }

        return userInfos;

    }

    @Override
    public List<DeadPrivateResponse> getPrivateDeadList(String userId) {

        UserAllInfoRequest userAllInfoRequest = restTemplateBuilder.build().getForObject(url + "/user/allinfo?uid=" + userId, UserAllInfoRequest.class);
        Map<UUID, FamilyRelation> relationMap = Stream.concat(userAllInfoRequest.getFamilyRelationList().stream(), userAllInfoRequest.getFriendRelationList().stream())
                .collect(Collectors.toMap(
                        relation -> UUID.fromString(relation.getConcernUid()),
                        relation -> relation
                ));

        return deadRepository.findAllByDeadIdIn(relationMap.keySet().stream().collect(Collectors.toList())).stream()
                .filter(dead -> !ObjectUtils.isEmpty(dead.getUserId()))
                .map(dead -> DeadPrivateResponse.builder()
                        .deadId(dead.getDeadId().toString())
                        .name(relationMap.get(dead.getDeadId()).getName())
                        .imageUrl(dead.getPortraitUrl())
                        .progressCheck(dead.isProgressCheck())
                        .build()
                ).collect(Collectors.toList());

    }

    @Override
    public void addDeadInfo(String deadId) {
        deadRepository.save(Dead.builder()
                .deadId(UUID.fromString(deadId))
                .build());
    }

    @Override
    public DeadInfo updateDeadInfo(DeadInfoRequest deadInfoRequest) {
        Dead dead = deadRepository.findByDeadId(UUID.fromString(deadInfoRequest.getDeadId())).orElseThrow(EntityNotFoundException::new);

        if (!ObjectUtils.isEmpty(dead.getUserId()) && !dead.getUserId().equals(UUID.fromString(deadInfoRequest.getUserId()))) {
            return null;
        }

        //????????? ??????
        dead.setUserId(UUID.fromString(deadInfoRequest.getUserId()));
        dead.setPublic(deadInfoRequest.getIsPublic());
        dead.setImprintDate(deadInfoRequest.getImprintDate());
        dead.setDeceasedDate(deadInfoRequest.getDeceasedDate());
        dead.setMurmurAddress(deadInfoRequest.getMurmurAddress());
        dead.setMurmurLat(deadInfoRequest.getMurmurLat());
        dead.setMurmurLng(deadInfoRequest.getMurmurLng());
        dead.setMurmurName(deadInfoRequest.getMurmurName());
        dead.setPortraitUrl(deadInfoRequest.getPortraitUrl());
        dead.setCemeteryName(deadInfoRequest.getCemeteryName());
        dead.setCemeteryAddress(deadInfoRequest.getCemeteryAddress());
        dead.setCemeteryLat(deadInfoRequest.getCemeteryLat());
        dead.setCemeteryLng(deadInfoRequest.getCemeteryLng());
        dead.setProgressCheck(LocalDate.now().toString().compareTo(deadInfoRequest.getImprintDate()) > 0 ? false : true);

        deadRepository.save(dead);

        return getDeadInfo(deadInfoRequest.getDeadId(), deadInfoRequest.getUserId());

    }

    @Override
    public List<DeadPrivateResponse> getSearchDead(String name) {


        // id, name, imageUrl, progressCheck
        UserInfo[] userInfos = restTemplateBuilder.build()
                .getForObject(url + "/user/find?name=" + name, UserInfo[].class);


        Map<UUID, UserInfo> userInfoMap = Arrays.stream(userInfos)
                .collect(Collectors.toMap(
                        userInfo -> UUID.fromString(userInfo.getId()),
                        userInfo -> userInfo
                ));

        return deadRepository.findAllByDeadIdIn(userInfoMap.keySet().stream().collect(Collectors.toList())).stream()
                .filter(dead -> !ObjectUtils.isEmpty(dead.getUserId()))
                .map(dead -> DeadPrivateResponse.builder()
                        .deadId(dead.getDeadId().toString())
                        .name(userInfoMap.get(dead.getDeadId()).getName())
                        .imageUrl(dead.getPortraitUrl())
                        .progressCheck(dead.isProgressCheck())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public DeadStatusResponse updateDeadStatus(DeadStatusRequest deadStatusRequest) {
        Dead dead = deadRepository.findByDeadId(UUID.fromString(deadStatusRequest.getDeadId())).orElseThrow(EntityNotFoundException::new);

        // 1. dead??? deadId??? req??? deadId??? ?????? ???
        // 2. dead??? userId??? null??? ??? (??????) -> req??? userId??? deadId??? ????????? ??????
        // 3. dead??? userId??? req??? userId??? ?????? ???
        if (ObjectUtils.isEmpty(dead.getUserId())) {
            if (!dead.getDeadId().equals(UUID.fromString(deadStatusRequest.getUserId()))) return null;
        } else if (!dead.getUserId().equals(UUID.fromString(deadStatusRequest.getUserId()))) {
            return null;
        }

        dead.setPublic(deadStatusRequest.isStatus());
        deadRepository.save(dead);

        return DeadStatusResponse.builder()
                .status(deadStatusRequest.isStatus())
                .build();
    }

    @Override
    public DeadPortraitResponse updateDeadPortrait(DeadPortraitRequest deadPortraitRequest) {
        Dead dead = deadRepository.findByDeadId(UUID.fromString(deadPortraitRequest.getDeadId())).orElseThrow(EntityNotFoundException::new);

        // 1. dead??? deadId??? req??? deadId??? ?????? ???
        // 2. dead??? userId??? null??? ??? (??????) -> req??? userId??? deadId??? ????????? ??????
        // 3. dead??? userId??? req??? userId??? ?????? ???
        if (ObjectUtils.isEmpty(dead.getUserId())) {
            if (!dead.getDeadId().equals(UUID.fromString(deadPortraitRequest.getUserId()))) return null;
        } else if (!dead.getUserId().equals(UUID.fromString(deadPortraitRequest.getUserId()))) {
            return null;
        }

        dead.setPortraitUrl(deadPortraitRequest.getImageUrl());
        deadRepository.save(dead);

        return DeadPortraitResponse.builder()
                .portraitUrl(deadPortraitRequest.getImageUrl())
                .build();
    }

    @Override
    public List<DeadManageResponse> getManageDeadList(String userId) {

        /*
         * 1. dead Table?????? ??? userId??? ???????????? ???????????? ?????????.
         *
         * 2. ??? ???????????? ??? ?????????. userInfo
         *
         * 3. ??????
         * */

        UserAllInfoRequest forObject = restTemplateBuilder.build().getForObject(url + "/user/allinfo?uid=" + userId, UserAllInfoRequest.class);

        // ????????? ?????? ???,
        if (ObjectUtils.isEmpty(forObject)) return null;

        Map<String, FamilyRelation> relationMap = forObject.getFamilyRelationList().stream()
                .collect(Collectors.toMap(
                        familyRelation -> familyRelation.getConcernUid(),
                        familyRelation -> familyRelation
                ));

        return deadRepository.findAllByUserId(UUID.fromString(userId)).stream()
                .map(dead -> DeadManageResponse.builder()
                        .deadId(dead.getDeadId().toString())
                        .imageUrl(dead.getPortraitUrl())
                        .name(relationMap.get(dead.getDeadId().toString()).getName())
                        .relationName(relationMap.get(dead.getDeadId().toString()).getRelationName())
                        .build())
                .collect(Collectors.toList());

    }

    @Override
    public List<String> changeUserList(List<String> userIdList) {
        List<Dead> allByDeadIdIn = deadRepository.findAllByDeadIdIn(userIdList.stream().map(s -> UUID.fromString(s)).collect(Collectors.toList()));
        return allByDeadIdIn.stream()
                .filter(dead -> ObjectUtils.isEmpty(dead.getUserId()))
                .map(dead -> dead.getDeadId().toString())
                .collect(Collectors.toList());
    }

}
