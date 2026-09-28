package com.wzbc.studyroom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzbc.studyroom.entity.BackupRecord;
import com.wzbc.studyroom.mapper.BackupRecordMapper;
import com.wzbc.studyroom.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库备份：调用 mysqldump 生成 SQL 文件，并把结果落成 backup_record。
 *
 * <p>几个工程细节：
 * <ul>
 *   <li>口令通过环境变量 {@code MYSQL_PWD} 传给子进程，<b>不写进命令行</b> ——
 *       命令行参数在 Windows 上可被同机其他进程通过进程列表看到；</li>
 *   <li>设置 60 秒超时并 destroy，避免 mysqldump 卡住把请求线程拖死；</li>
 *   <li>失败一律记 status=0 并写明原因，不抛异常。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {

    private final BackupRecordMapper backupRecordMapper;

    @Value("${spring.datasource.url}")
    private String dsUrl;

    @Value("${spring.datasource.username:}")
    private String dsUser;

    @Value("${spring.datasource.password:}")
    private String dsPwd;

    /** 备份文件目录；默认放在项目下的 backups/ */
    @Value("${backup.dir:backups}")
    private String backupDir;

    /** mysqldump 可执行文件；不在 PATH 时请在本地配置里写绝对路径 */
    @Value("${backup.mysqldump:mysqldump}")
    private String mysqldump;

    private static final Pattern URL_PATTERN =
            Pattern.compile("jdbc:mysql://([^:/]+):(\\d+)/([^?]+)");

    @Override
    public BackupRecord backup(Long operatorId, int backupType) {
        BackupRecord record = new BackupRecord();
        record.setBackupType(backupType);
        record.setOperatorId(operatorId);
        record.setStatus(1);
        record.setCreatedAt(LocalDateTime.now());

        Matcher m = URL_PATTERN.matcher(dsUrl == null ? "" : dsUrl);
        if (!m.find()) {
            return fail(record, "无法从数据源地址解析出主机/端口/库名：" + dsUrl);
        }
        String host = m.group(1);
        String port = m.group(2);
        String db = m.group(3);

        try {
            Path dir = Paths.get(backupDir).toAbsolutePath();
            Files.createDirectories(dir);

            String fileName = db + "_" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".sql";
            Path file = dir.resolve(fileName);

            ProcessBuilder pb = new ProcessBuilder(
                    mysqldump,
                    "-h", host,
                    "-P", port,
                    "-u", dsUser,
                    "--databases", db,
                    "--default-character-set=utf8mb4",
                    "--single-transaction",
                    "--result-file=" + file.toAbsolutePath());
            // 口令走环境变量，不出现在命令行里
            pb.environment().put("MYSQL_PWD", dsPwd == null ? "" : dsPwd);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return fail(record, "mysqldump 执行超时（60 秒），已强制终止");
            }
            int code = process.exitValue();

            File f = file.toFile();
            record.setFileName(fileName);
            record.setFilePath(file.toAbsolutePath().toString());

            if (code != 0) {
                record.setFileSize(f.exists() ? f.length() : 0L);
                return fail(record, "mysqldump 退出码 " + code + "：" + brief(output));
            }
            if (!f.exists() || f.length() == 0) {
                return fail(record, "mysqldump 成功但未生成有效文件");
            }

            record.setFileSize(f.length());
            record.setRemark("备份成功");
            backupRecordMapper.insert(record);
            log.info("数据库备份完成：{}（{} 字节）", fileName, f.length());
            return record;
        } catch (Exception e) {
            return fail(record, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public List<BackupRecord> list() {
        return backupRecordMapper.selectList(new LambdaQueryWrapper<BackupRecord>()
                .orderByDesc(BackupRecord::getCreatedAt)
                .last("LIMIT 100"));
    }

    /** 失败也要留痕：写一条 status=0 的记录再返回，不抛异常 */
    private BackupRecord fail(BackupRecord record, String reason) {
        record.setStatus(0);
        record.setRemark(brief(reason));
        if (record.getFileSize() == null) {
            record.setFileSize(0L);
        }
        backupRecordMapper.insert(record);
        log.warn("数据库备份失败：{}", record.getRemark());
        return record;
    }

    private String brief(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= 400 ? t : t.substring(0, 400) + "...";
    }
}
