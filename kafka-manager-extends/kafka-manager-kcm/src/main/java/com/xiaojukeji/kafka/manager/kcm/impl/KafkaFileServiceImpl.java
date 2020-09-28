package com.xiaojukeji.kafka.manager.kcm.impl;

import com.xiaojukeji.kafka.manager.common.bizenum.KafkaFileEnum;
import com.xiaojukeji.kafka.manager.common.entity.Result;
import com.xiaojukeji.kafka.manager.common.entity.ResultStatus;
import com.xiaojukeji.kafka.manager.common.entity.dto.normal.KafkaFileDTO;
import com.xiaojukeji.kafka.manager.common.utils.CopyUtils;
import com.xiaojukeji.kafka.manager.common.utils.ValidateUtils;
import com.xiaojukeji.kafka.manager.dao.KafkaFileDao;
import com.xiaojukeji.kafka.manager.common.entity.pojo.KafkaFileDO;
import com.xiaojukeji.kafka.manager.kcm.component.storage.AbstractStorageService;
import com.xiaojukeji.kafka.manager.kcm.KafkaFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhongyuankai
 * @date 2020/5/7
 */
@Service("kafkaFileService")
public class KafkaFileServiceImpl implements KafkaFileService {
    private final static Logger LOGGER = LoggerFactory.getLogger(KafkaFileServiceImpl.class);

    @Autowired
    private KafkaFileDao kafkaFileDao;

    @Autowired
    private AbstractStorageService storageService;

    @Override
    public ResultStatus uploadKafkaFile(KafkaFileDTO kafkaFileDTO, String username) {
        if (!kafkaFileDTO.createParamLegal()) {
            return ResultStatus.PARAM_ILLEGAL;
        }

        KafkaFileDO kafkaFileDO = new KafkaFileDO();
        CopyUtils.copyProperties(kafkaFileDO, kafkaFileDTO);
        kafkaFileDO.setOperator(username);
        try {
            if (kafkaFileDao.insert(kafkaFileDO) <= 0) {
                return ResultStatus.MYSQL_ERROR;
            }
            if (!storageService.upload(
                    kafkaFileDTO.getFileName(),
                    kafkaFileDTO.getFileMd5(),
                    kafkaFileDTO.getUploadFile())
                    ) {
                kafkaFileDao.deleteById(kafkaFileDO.getId());
                return ResultStatus.UPLOAD_FILE_FAIL;
            }
            return ResultStatus.SUCCESS;
        } catch (DuplicateKeyException e) {
            return ResultStatus.RESOURCE_ALREADY_EXISTED;
        } catch (Exception e) {
            LOGGER.error("upload kafka file failed, kafkaFileDTO:{}.", kafkaFileDTO, e);
        }
        return ResultStatus.MYSQL_ERROR;
    }

    @Override
    public ResultStatus modifyKafkaFile(KafkaFileDTO kafkaFileDTO, String userName) {
        if (ValidateUtils.isNull(kafkaFileDTO) || !kafkaFileDTO.modifyParamLegal()) {
            return ResultStatus.PARAM_ILLEGAL;
        }

        KafkaFileDO kafkaFileDO = null;
        try {
            kafkaFileDO = kafkaFileDao.getById(kafkaFileDTO.getId());
            if (ValidateUtils.isNull(kafkaFileDO)) {
                return ResultStatus.RESOURCE_NOT_EXIST;
            }
            KafkaFileEnum kafkaFileEnum = KafkaFileEnum.getByCode(kafkaFileDO.getFileType());
            if (ValidateUtils.isNull(kafkaFileEnum)) {
                return ResultStatus.OPERATION_FAILED;
            }
            if (!kafkaFileDTO.getFileName().endsWith(kafkaFileEnum.getSuffix())) {
                return ResultStatus.OPERATION_FAILED;
            }

            KafkaFileDO newKafkaFileDO = new KafkaFileDO();
            newKafkaFileDO.setId(kafkaFileDO.getId());
            newKafkaFileDO.setFileName(kafkaFileDTO.getFileName());
            newKafkaFileDO.setFileMd5(kafkaFileDTO.getFileMd5());
            newKafkaFileDO.setDescription(kafkaFileDTO.getDescription());
            newKafkaFileDO.setOperator(userName);
            if (kafkaFileDao.updateById(newKafkaFileDO) <= 0) {
                return ResultStatus.MYSQL_ERROR;
            }
        } catch (DuplicateKeyException e) {
            return ResultStatus.RESOURCE_NAME_DUPLICATED;
        } catch (Exception e) {
            LOGGER.error("modify kafka file failed, kafkaFileDTO:{}.", kafkaFileDTO, e);
            return ResultStatus.MYSQL_ERROR;
        }

        if (storageService.upload(
                kafkaFileDTO.getFileName(),
                kafkaFileDTO.getFileMd5(),
                kafkaFileDTO.getUploadFile())
                ) {
            return ResultStatus.SUCCESS;
        }

        try {
            if (kafkaFileDao.updateById(kafkaFileDO) <= 0) {
                return ResultStatus.MYSQL_ERROR;
            }
            return ResultStatus.UPLOAD_FILE_FAIL;
        } catch (Exception e) {
            LOGGER.error("rollback modify kafka file failed, kafkaFileDTO:{}.", kafkaFileDTO, e);
        }
        return ResultStatus.MYSQL_ERROR;
    }

    @Override
    public ResultStatus deleteKafkaFile(Long id) {
        try {
            if (kafkaFileDao.deleteById(id) > 0) {
                return ResultStatus.SUCCESS;
            }
        } catch (Exception e) {
            LOGGER.error("delete kafka file failed, id:{}.", id, e);
        }
        return ResultStatus.MYSQL_ERROR;
    }

    @Override
    public List<KafkaFileDO> getKafkaFiles() {
        try {
            return kafkaFileDao.list();
        } catch (Exception e) {
            LOGGER.error("get kafka file list failed.", e);
        }
        return new ArrayList<>();
    }

    @Override
    public KafkaFileDO getFileById(Long id) {
        try {
            return kafkaFileDao.getById(id);
        } catch (Exception e) {
            LOGGER.error("get kafka file failed, id:{}.", id, e);
        }
        return null;
    }

    @Override
    public KafkaFileDO getFileByFileName(String fileName) {
        try {
            return kafkaFileDao.getFileByFileName(fileName);
        } catch (Exception e) {
            LOGGER.error("get kafka file failed, fileName:{}.", fileName, e);
        }
        return null;
    }

    @Override
    public Result<String> downloadKafkaConfigFile(Long fileId) {
        KafkaFileDO kafkaFileDO = kafkaFileDao.getById(fileId);
        if (ValidateUtils.isNull(kafkaFileDO)) {
            return Result.buildFrom(ResultStatus.RESOURCE_NOT_EXIST);
        }
        if (KafkaFileEnum.PACKAGE.getCode().equals(kafkaFileDO.getFileType())) {
            return Result.buildFrom(ResultStatus.FILE_TYPE_NOT_SUPPORT);
        }

        return storageService.download(kafkaFileDO.getFileName(), kafkaFileDO.getFileMd5());
    }

    @Override
    public String getDownloadBaseUrl() {
        return storageService.getDownloadBaseUrl();
    }
}
