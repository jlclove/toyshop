package com.lianjia.sh.se.tools.db2j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.lianjia.sh.se.tools.db2j.DbM.Column;
import com.lianjia.sh.se.tools.db2j.adaptor.JavaAdaptor;

public class ClassGenerator {

  public static void main(String[] args) throws FileNotFoundException, IOException {

    List<Column> columnList = new ArrayList<>();
    columnList.add(new Column("id", "bigint", false, "主键ID", null));
    columnList.add(new Column("old", "bit", true, "是否老年", "1"));
    javaClassGenerate("mail", "com.lj.test", "胡大叔", columnList);
  }

  public static String javaClassGenerate(String className, String packageName, String authorName, List<Column> columnList)
      throws FileNotFoundException, IOException {
    LocalDateTime dt = LocalDateTime.now();

    String path = DbM.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    path = path.substring(0, path.lastIndexOf("/"));
    String fileName = path + "/output/" + className + ".java";

    File file = new File(fileName);
    if (!file.getParentFile().exists()) {
      file.getParentFile().mkdirs();
    }

    // System.out.println("----+========" + fileName);
    try (OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8")) {
      fw.write("package " + packageName + ";\n\n");

      if (columnList.stream().anyMatch(column -> JavaAdaptor.hasMSSQLDate(column.getColType()))) {
        fw.write("import java.util.Date;\n");
      }
      if (columnList.stream().anyMatch(column -> JavaAdaptor.hasMSSQLBigPrecision(column.getColType()))) {
        fw.write("import java.math.BigDecimal;\n");
      }
      fw.write("\n\n");
      fw.write("/**\n");
      fw.write(" * " + className + "\n");
      fw.write(" * \n");
      fw.write(" * @author " + authorName + "\n");
      fw.write(" * @createAt " + dt.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")) + "\n");
      fw.write(" * @Copyright (c) " + dt.getYear() + ", Lianjia Group All Rights Reserved.\n");
      fw.write(" */\n");
      fw.write("public class " + className + "{\n\n");

      columnList.forEach(column -> {
        try {
          // private
          fw.write("  /**\n");
          fw.write("   * " + column.getComment() + "\n");
          fw.write("   */\n");
          fw.write("  private " + JavaAdaptor.typeFromMSSQL(column.getColType(), column.isNullable()) + " " + column.getName() + ";\n\n");
        } catch (Exception e) {
          e.printStackTrace();
        }
      });

      columnList.forEach(column -> {
        try {
          // getter
          fw.write("  /**\n");
          fw.write("   * 获得" + column.getComment() + "\n");
          fw.write("   * \n");
          fw.write("   * @return the " + column.getName() + "\n");
          fw.write("   */\n");
          fw.write("  public " + JavaAdaptor.typeFromMSSQL(column.getColType(), column.isNullable()) + " "
              + (JavaAdaptor.hasMSSQLBoolean(column.getColType()) ? "is" : "get") + column.getName().substring(0, 1).toUpperCase()
              + column.getName().substring(1) + "(){\n");
          fw.write("    return this." + column.getName() + ";\n");
          fw.write("  }\n\n");

          // setter
          fw.write("  /**\n");
          fw.write("   * 设置" + column.getComment() + "\n");
          fw.write("   * \n");
          fw.write("   * @param " + column.getName() + " " + JavaAdaptor.typeFromMSSQL(column.getColType(), column.isNullable()) + "\n");
          fw.write("   */\n");
          fw.write("  public void set" + column.getName().substring(0, 1).toUpperCase() + column.getName().substring(1) + "("
              + JavaAdaptor.typeFromMSSQL(column.getColType(), column.isNullable()) + " " + column.getName() + "){\n");
          fw.write("    this." + column.getName() + " = " + column.getName() + ";\n");
          fw.write("  }\n\n");
        } catch (Exception e) {
          e.printStackTrace();
        }
      });

      // toString
      fw.write("  @Override\n");
      fw.write("  public String toString() {\n");
      fw.write("    return \"" + className + " [");
      for (int i = 0; i < columnList.size(); i++) {
        fw.write(columnList.get(i).getName() + "=\"+ " + columnList.get(i).getName() + " +\"");
        if (i < columnList.size() - 1) {
          fw.write(",");
        }
      }
      fw.write("]\";\n");
      fw.write("  }\n");

      fw.write("\n}\n");

      fw.flush();
      fw.close();
    }
    return fileName;
  }

}
