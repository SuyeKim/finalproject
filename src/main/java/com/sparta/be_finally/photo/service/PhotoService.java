package com.sparta.be_finally.photo.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import com.sparta.be_finally.config.S3.AwsS3Service;
import com.sparta.be_finally.config.dto.PrivateResponseBody;
import com.sparta.be_finally.config.errorcode.CommonStatusCode;
import com.sparta.be_finally.config.exception.RestApiException;
import com.sparta.be_finally.config.util.SecurityUtil;
import com.sparta.be_finally.config.validator.Validator;
import com.sparta.be_finally.photo.dto.FrameResponseDto;
import com.sparta.be_finally.photo.dto.PhotoRequestDto;
import com.sparta.be_finally.photo.entity.Photo;
import com.sparta.be_finally.photo.repository.PhotoRepository;
import com.sparta.be_finally.room.dto.RoomResponseDto;
import com.sparta.be_finally.room.entity.Room;
import com.sparta.be_finally.room.repository.RoomRepository;
import com.sparta.be_finally.user.entity.User;
import io.openvidu.java.client.OpenVidu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PhotoService {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final AwsS3Service awsS3Service;
    private final Validator validator;
    private final PhotoRepository photoRepository;

    private final AmazonS3Client amazonS3Client;

    private OpenVidu openVidu;

    // OpenVidu 서버가 수신하는 URL
    @Value("${openvidu.url}")
    private String OPENVIDU_URL;

    // OpenVidu 서버와 공유되는 비밀
    @Value("${openvidu.secret}")
    private String OPENVIDU_SECRET;

    @PostConstruct
    public OpenVidu openVidu() {
        return openVidu = new OpenVidu(OPENVIDU_URL, OPENVIDU_SECRET);
    }

    //사진 촬영 준비
    @Transactional
    public FrameResponseDto photoShoot(Long roomId) {
        User user = SecurityUtil.getCurrentUser();
        // 1. roomId 존재 여부 확인
        Room room = validator.existsRoom(roomId);

        // 2. 입장한 방 - 선택한 프레임 번호
        //int frameNum = room.getFrame();

        return new FrameResponseDto(room.getFrame(), room.getFrameUrl());
    }


    // 찍은 사진 S3 저장

    @Transactional
    public PrivateResponseBody photoShootSave(Long roomId, PhotoRequestDto photoRequestDto) {
        // 1. roomId 존재 여부 확인
        Room room = validator.existsRoom(roomId);

        // 2. Photo 테이블 - room_id 에서 촬영한 사진 조회
        //    사진을 한 컷 이상 찍은 상태 : isExist 에 정보 저장 됨
        //    photo_one 촬영 한 상태 : isExist = null
        Photo photo = photoRepository.findByRoomId(roomId).orElse(null);

        //String folderName = String.valueOf(createFolder(bucket + "/contact", today));


        // 3. photoRequestDto 에 있는 파일 S3에 업로드
        if (photoRequestDto.getPhoto_1() != null && !photoRequestDto.getPhoto_1().getContentType().isEmpty()) {
            String photo_one_imgUrl = awsS3Service.uploadFile(photoRequestDto.getPhoto_1(), room.getId());
            photoRepository.saveAndFlush(new Photo(room, photo_one_imgUrl));

        } else if (photoRequestDto.getPhoto_2() != null && !photoRequestDto.getPhoto_2().getContentType().isEmpty()) {
            String photo_two_imgUrl = awsS3Service.uploadFile(photoRequestDto.getPhoto_2(), room.getId());
            photo.photo_two_update(photo_two_imgUrl);

        } else if (photoRequestDto.getPhoto_3() != null && !photoRequestDto.getPhoto_3().getContentType().isEmpty()) {
            String photo_three_imgUrl = awsS3Service.uploadFile(photoRequestDto.getPhoto_3(), room.getId());
            photo.photo_three_update(photo_three_imgUrl);

        } else if (photoRequestDto.getPhoto_4() != null && !photoRequestDto.getPhoto_4().getContentType().isEmpty()) {
            String photo_four_imgUrl = awsS3Service.uploadFile(photoRequestDto.getPhoto_4(), room.getId());
            photo.photo_four_update(photo_four_imgUrl);

        } else {
            throw new RestApiException(CommonStatusCode.SHOOT_PHOTO_FAIL);
        }
        return new PrivateResponseBody(CommonStatusCode.SHOOT_PHOTO_SUCCESS);
    }






    @Transactional(readOnly = true)
    public PrivateResponseBody photoGet(Long roomId) {


        Room room = validator.existsRoom(roomId);

        ObjectListing objectListing = amazonS3Client.listObjects(bucket, "photo/"+room.getId() + "/");
        List<S3ObjectSummary> s3ObjectSummaries = objectListing.getObjectSummaries();

        List<String> imgUrlList = Lists.newArrayList();

        for (S3ObjectSummary s3Object : s3ObjectSummaries) {
            String imgKey = s3Object.getKey();
            String imgUrl = amazonS3Client.getResourceUrl(bucket, imgKey);
            imgUrlList.add(imgUrl);
        }
        //return imgUrlList;
        return new PrivateResponseBody(CommonStatusCode.SHOOT_PHOTO_GET, imgUrlList,new FrameResponseDto(room.getFrame(),room.getFrameUrl()),room.getId());
    }
}

