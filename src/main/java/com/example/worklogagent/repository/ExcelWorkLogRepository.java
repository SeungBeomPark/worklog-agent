package com.example.worklogagent.repository;

import com.example.worklogagent.config.AppProperties;
import com.example.worklogagent.model.WorkLogEntry;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 엑셀(.xls) 기반 저장소 구현. 원본 파일을 직접 읽고 고쳐쓴다.
 *
 * worklog.storage=excel 일 때만 빈으로 등록된다(StorageCondition).
 *
 * 파일 규칙(업로드 양식 기준):
 *   0행: 빈 행, 1행: 헤더, 2행~: 데이터
 *   A=날짜(yyyyMMdd), B=업무유형, C=프로젝트, D=업무내용
 *   월별 파일: filePrefix + yyyyMM 로 시작하는 파일
 */
@Repository
@Conditional(StorageCondition.Excel.class)
public class ExcelWorkLogRepository implements WorkLogRepository {

    private static final Logger log = LoggerFactory.getLogger(ExcelWorkLogRepository.class);

    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private static final int COL_DATE = 0, COL_TYPE = 1, COL_PROJECT = 2, COL_CONTENT = 3;
    private static final int HEADER_ROW = 1, FIRST_DATA_ROW = 2;

    private final AppProperties props;

    public ExcelWorkLogRepository(AppProperties props) {
        this.props = props;
    }

    @Override
    public Optional<WorkLogEntry> findByDate(LocalDate date) {
        List<WorkLogEntry> list = findByDateRange(date, date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<WorkLogEntry> findByDateRange(LocalDate from, LocalDate to) {
        List<WorkLogEntry> result = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate lastMonth = to.withDayOfMonth(1);
        while (!cursor.isAfter(lastMonth)) {
            File file = resolveMonthlyFile(cursor, false);
            if (file != null && file.exists()) {
                result.addAll(readEntries(file, from, to));
            }
            cursor = cursor.plusMonths(1);
        }
        result.sort(Comparator.comparing(WorkLogEntry::date));
        return result;
    }

    @Override
    public List<WorkLogEntry> findByMonth(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
        return findByDateRange(first, last);
    }

    @Override
    public void save(WorkLogEntry entry) {
        LocalDate date = entry.date();
        File file = resolveMonthlyFile(date, true); // 없으면 생성 대상 경로 반환

        try {
            Workbook wb;
            if (file.exists()) {
                try (InputStream is = new FileInputStream(file)) {
                    wb = WorkbookFactory.create(is);
                }
            } else {
                wb = new HSSFWorkbook();          // .xls 새 파일
                Sheet s = wb.createSheet("Sheet1");
                writeHeader(s);
            }

            Sheet sheet = wb.getSheetAt(0);
            String key = date.format(DATE_KEY);
            DataFormatter fmt = new DataFormatter();

            // 기존 행 찾기
            Row target = null;
            for (Row row : sheet) {
                if (row.getRowNum() < FIRST_DATA_ROW) continue;
                String d = fmt.formatCellValue(row.getCell(COL_DATE)).trim();
                if (key.equals(d)) { target = row; break; }
            }
            // 없으면 새 행 추가
            if (target == null) {
                int newRowNum = Math.max(sheet.getLastRowNum() + 1, FIRST_DATA_ROW);
                target = sheet.createRow(newRowNum);
            }
            writeRow(target, entry);

            try (OutputStream os = new FileOutputStream(file)) {
                wb.write(os);
            }
            wb.close();
            log.info("업무일지 저장: {}", date);

        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 저장 실패: " + date, e);
        }
    }

    @Override
    public void deleteByDate(LocalDate date) {
        File file = resolveMonthlyFile(date, false);
        if (file == null || !file.exists()) return;

        try {
            Workbook wb;
            try (InputStream is = new FileInputStream(file)) {
                wb = WorkbookFactory.create(is);
            }
            Sheet sheet = wb.getSheetAt(0);
            String key = date.format(DATE_KEY);
            DataFormatter fmt = new DataFormatter();

            Row target = null;
            for (Row row : sheet) {
                if (row.getRowNum() < FIRST_DATA_ROW) continue;
                String d = fmt.formatCellValue(row.getCell(COL_DATE)).trim();
                if (key.equals(d)) { target = row; break; }
            }
            if (target != null) {
                int rowNum = target.getRowNum();
                int lastRow = sheet.getLastRowNum();
                // 아래 행들을 한 칸씩 위로 당겨 빈 줄이 남지 않게
                if (rowNum < lastRow) {
                    sheet.shiftRows(rowNum + 1, lastRow, -1);
                } else {
                    sheet.removeRow(target);
                }
                try (OutputStream os = new FileOutputStream(file)) {
                    wb.write(os);
                }
                log.info("업무일지 삭제: {}", date);
            }
            wb.close();
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 삭제 실패: " + date, e);
        }
    }

    // ── 내부 유틸 ──

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(HEADER_ROW);
        header.createCell(COL_DATE).setCellValue("날짜");
        header.createCell(COL_TYPE).setCellValue("업무유형");
        header.createCell(COL_PROJECT).setCellValue("프로젝트");
        header.createCell(COL_CONTENT).setCellValue("업무 내용");
    }

    private void writeRow(Row row, WorkLogEntry e) {
        setCell(row, COL_DATE, e.date().format(DATE_KEY));
        setCell(row, COL_TYPE, e.type());
        setCell(row, COL_PROJECT, e.project());
        setCell(row, COL_CONTENT, e.content());
    }

    private void setCell(Row row, int col, String value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
    }

    private List<WorkLogEntry> readEntries(File file, LocalDate from, LocalDate to) {
        List<WorkLogEntry> entries = new ArrayList<>();
        try (InputStream is = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            for (Row row : sheet) {
                if (row.getRowNum() < FIRST_DATA_ROW) continue;
                String dateStr = fmt.formatCellValue(row.getCell(COL_DATE)).trim();
                if (dateStr.isEmpty()) continue;
                LocalDate d;
                try {
                    d = LocalDate.parse(dateStr, DATE_KEY);
                } catch (Exception ex) {
                    continue;
                }
                if (d.isBefore(from) || d.isAfter(to)) continue;
                entries.add(new WorkLogEntry(
                        d,
                        fmt.formatCellValue(row.getCell(COL_TYPE)).trim(),
                        fmt.formatCellValue(row.getCell(COL_PROJECT)).trim(),
                        fmt.formatCellValue(row.getCell(COL_CONTENT)).trim()
                ));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 읽기 실패: " + file.getName(), e);
        }
        return entries;
    }

    /** 해당 월 파일 찾기. createIfMissing=true 면 못 찾아도 생성용 경로를 만들어 반환 */
    private File resolveMonthlyFile(LocalDate anyDayInMonth, boolean createIfMissing) {
        String monthPrefix = props.worklog().filePrefix() + anyDayInMonth.format(MONTH_KEY);
        File dir = new File(props.worklog().directory());
        File[] candidates = dir.listFiles((d, name) ->
                name.startsWith(monthPrefix)
                        && (name.endsWith(".xls") || name.endsWith(".xlsx")));

        if (candidates != null && candidates.length > 0) {
            File chosen = candidates[0];
            for (File f : candidates) {
                if (f.lastModified() > chosen.lastModified()) chosen = f;
            }
            return chosen;
        }
        if (createIfMissing) {
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, monthPrefix + ".xls"); // 예: MmList_202607.xls
        }
        return null;
    }
}
