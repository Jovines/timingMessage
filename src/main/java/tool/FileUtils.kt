package tool

import bean.AssistantStudent
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.regex.Pattern


object FileUtils {
    fun writeFile(content: String) {
        val currentDir = """Data"""
        val file = File(currentDir, "leaveData.txt")
        file.writeText(content)
    }
    fun writeFileAdd() {
        val currentDir = System.getProperty("user.dir") + "\\out"
        val file = File(currentDir, "leaveData.txt")
        //追加方式写入字节或字符
        file.appendBytes(byteArrayOf(93, 85, 74, 93))
        file.appendText("吼啊")
        println(file.readText())
    }

    fun readOnDutyDataExcel() {
        val sqlUtil = SqlUtil()
        val filePath = """Data/OnDutyDate.xls"""
        sqlUtil.statement.execute("delete from watchList")
        val fileInputStream = FileInputStream(filePath)
        val bufferedInputStream = BufferedInputStream(fileInputStream)
        val fileSystem = POIFSFileSystem(bufferedInputStream)
        val workbook = HSSFWorkbook(fileSystem)
        for (sheetIndex in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIndex)
            val lastRowIndex = sheet.lastRowNum
            val rowLimit = 2
            var rowignore = 0
            var turnIndex = 0
            for (i in 2..lastRowIndex) {
                if (rowignore % rowLimit == 0) {
                    turnIndex++
                }
                val row = sheet.getRow(i) ?: break
                val lastCellNum = row.lastCellNum
                var 是否记录本行 = false
                for (j in 1 until lastCellNum) {
                    if (row.getCell(j) != null) {
                        是否记录本行 = true
                    }
                    row.getCell(j)?.cellType = CellType.STRING
                }
                if (是否记录本行) {
                    val limit = 3
                    var ignore = 0
                    var index = 1
                    for (j in 1 until lastCellNum) {
                        if (ignore % limit == 0) {
                            val data = row.getCell(j).stringCellValue
                            val p = Pattern.compile("[\\u2E80-\\u9FFF]+")
                            val m = p.matcher(data)
                            if (m.find()) {
                                val name = m.group(0)
                                sqlUtil.statement.execute("insert into watchList (type, weekDay, timeTurn, name) values ('${sheet.sheetName}',${index},${turnIndex},'${name}');")
                            }
                            index++
                        }
                        ignore++
                    }
                }
                rowignore++
            }
        }
        bufferedInputStream.close()
        sqlUtil.close()
    }


    fun readStudentDataExcel() {
        val sqlUtil = SqlUtil()
        sqlUtil.statement.execute("delete from assistantstudent")
        val filePath = """Data/StudentsDate.xls"""
        val fileInputStream = FileInputStream(filePath)
        val bufferedInputStream = BufferedInputStream(fileInputStream)
        val fileSystem = POIFSFileSystem(bufferedInputStream)
        val workbook = HSSFWorkbook(fileSystem)
        val sheet = workbook.getSheetAt(0)
        val lastRowIndex = sheet.lastRowNum
        for (i in 1..lastRowIndex) {
            val row = sheet.getRow(i) ?: break
            val lastCellNum = row.lastCellNum
            var 是否记录本行 = false
            for (j in 0 until lastCellNum) {
                if (row.getCell(j) != null) {
                    是否记录本行 = true
                }
                row.getCell(j)?.cellType = CellType.STRING
            }
            if (是否记录本行) {
                row.getCell(2)?.let {
                    val assistantStudent = AssistantStudent().apply {
                        for (j in 0 until lastCellNum) {
                            row.getCell(j)?.let {
                                val p = Pattern.compile("[\\u2E80-\\u9FFF0-9a-zA-Z]+")
                                val m = p.matcher(it.stringCellValue)
                                if (m.find()) {
                                    this[j] = m.group(0)
                                }
                            }
                        }
                    }
                    val sql = "insert into assistantstudent (name, studentId, type, phoneNumber, qqNumber) values ('${assistantStudent.name}',${assistantStudent.studentID},'${assistantStudent.type}',${assistantStudent.phoneNumber},${assistantStudent.qqNumber})"
                    sqlUtil.statement.execute(sql)
                }
            }
        }
        bufferedInputStream.close()
        sqlUtil.close()
    }


    fun createExcel() {
        val sqlUtil = SqlUtil()
        val filePath = "Data/请假记录文档.xls"
        val file = File(filePath)
        val outputStream: FileOutputStream
        try {
            outputStream = FileOutputStream(file)
        } catch (e: Exception) {
            print(e.message)
            return
        }
        val workbook = HSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")
        val row = sheet.createRow(0)
        row.createCell(0).setCellValue("学工类型")
        row.createCell(1).setCellValue("学号")
        row.createCell(2).setCellValue("姓名")
        row.createCell(3).setCellValue("事由")
        row.createCell(4).setCellValue("请假班次")
        row.createCell(5).setCellValue("何时请的假")
        val sql = "select * from leavedata order by STR_TO_DATE(leaveTime,'%Y年%m月%d日') desc;"
        val resultSet = sqlUtil.statement.executeQuery(sql)
        var i = 0
        while (resultSet.next()) {
            val row1 = sheet.createRow(i + 1)
            row1.createCell(0).setCellValue(resultSet.getString("type"))
            row1.createCell(1).setCellValue(resultSet.getLong("studentId").toString())
            row1.createCell(2).setCellValue(resultSet.getString("name"))
            row1.createCell(3).setCellValue(resultSet.getString("content").substringAfter("节课时间段值班请假").substring(4))
            row1.createCell(4).setCellValue(resultSet.getString("leaveTime"))
            row1.createCell(5).setCellValue(resultSet.getString("time"))
            i++
        }

        // 单元格样式
        val cellStyle = workbook.createCellStyle()
        cellStyle.alignment = HorizontalAlignment.CENTER
        //设置自动换行
        cellStyle.wrapText = true

        for (i in 0 until 6) {
            sheet.autoSizeColumn(i);//先设置自动列宽
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) * 17 / 10);//设置列宽为自动列宽的1.7倍（当然不是严格的1.7倍，int的除法恕不再讨论），这个1.6左右也可以，这是本人测试的经验值*
        }

        workbook.setActiveSheet(0)
        workbook.write(outputStream)
        outputStream.close()
        sqlUtil.close()
    }


    fun readFile(): String {
        val filename = """Data/leaveData.txt"""
        val file = File(filename)
        return file.readText()
    }

}

