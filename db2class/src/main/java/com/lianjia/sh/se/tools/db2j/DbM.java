package com.lianjia.sh.se.tools.db2j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DbM {

  static Scanner scanner = new Scanner(System.in);

  static String version = "1.3.0";
  static String author = "Jail Hu";

  // 表名正则
  static String tableNamePatt;
  // 表名正则提取下标
  static int tableNamePattIndex;
  static int tableNamePattTotal = 0;
  // 类名连接符
  static String nameSplit;
  // 类名转换方式， 0：原始 1：驼峰
  static int namedType = -1;


  public static void main(String[] args) throws SQLException {
    System.out.println("—————————————————————————————————————————————————\n");
    System.out.println("　　　　　【上海链家SE】数据库对象导出工具 【Ver" + version + "】");
    System.out.println("                        Author:" + author + "\n");
    System.out.println("—————————————————————————————————————————————————");

    String dbUrl = getInput("请输入需要连接的数据库地址：");
    String user = getInput("请输入数据库 [" + dbUrl + "] 的登录用户名：");
    String password = getInput("请输入数据库 [" + dbUrl + "] 的登录密码：");
    // Connection conn = DriverManager.getConnection("jdbc:sqlserver://JAIL-PC\\SQLEXPRESS;", "sa",
    // "hujie");

    System.out.println("开始连接数据库 [" + dbUrl + "] ...\n");
    if (dbUrl.contains("\\\\")) {
      dbUrl = dbUrl.replace("\\\\", "\\");
    }
    Connection conn = DriverManager.getConnection("jdbc:sqlserver://" + dbUrl + ";", user, password);
    String dbName = databaseOperate(conn);
    tableOperate(conn, dbName);
    scanner.close();
  }

  private static String databaseOperate(Connection conn) throws SQLException {
    Statement stmt = conn.createStatement();
    stmt.execute("SELECT dbid,name FROM master.sys.SysDatabases ORDER BY dbid");
    ResultSet set = stmt.getResultSet();
    System.out.println("[database list]");
    Map<Integer, String> dbMap = new HashMap<>();
    while (set.next()) {
      dbMap.put(set.getInt(1), set.getString(2));
      System.out.println(set.getInt(1) + ": " + set.getString(2));
    }
    set.close();
    stmt.close();
    System.out.println("-----------------------");

    ScannerInput<Integer> si = new ScannerInput<Integer>() {
      @Override
      public Integer getLegalInput(String input) throws Exception {
        int index = Integer.parseInt(input);
        if (dbMap.containsKey(index)) {
          return index;
        } else {
          throw new Exception();
        }
      }

      @Override
      public String getWelcomeMsg() {
        return "请选择需要导出的数据库序号：";
      }

      @Override
      public String getErrorMsg() {
        return "---->>数据库序号输入错误";
      }
    };
    String dbName = dbMap.get(si.get());
    System.out.println("您选择的数据库为：" + dbName + "\n");
    return dbName;
  }


  static interface ScannerInput<R> extends Supplier<R> {

    default int getRetryCount() {
      return 3;
    }

    R getLegalInput(String input) throws Exception;

    String getWelcomeMsg();

    String getErrorMsg();

    /*
     * @see java.util.function.Supplier#get()
     */
    @Override
    default R get() {
      return this.get(0);
    }

    default R get(int i) {
      String input = getInput(this.getWelcomeMsg());
      try {
        return this.getLegalInput(input);
      } catch (Exception e) {
        if (i < this.getRetryCount()) {
          System.err.println(this.getErrorMsg() + " 请重试 " + (i + 1) + "/" + this.getRetryCount() + "\n");
          i++;
          return get(i);
        } else {
          throw new NumberFormatException((this.getRetryCount() > 0 ? "重试结束，" : "") + "参数输入有误，程序异常跳出");
        }
      }
    }
  }

  private static String tableOperate(Connection conn, String dbName) throws SQLException {
    Statement stmt = conn.createStatement();
    stmt.execute("SELECT s.id,cast(s.name as nvarchar(50)) as name,cast(p.value as nvarchar(200)) as comment FROM " + dbName
        + ".sys.SysObjects as s left join " + dbName
        + ".sys.extended_properties as p on s.id = p.major_id and p.minor_id=0 Where s.XType='U' ORDER BY s.Name");
    ResultSet set = stmt.getResultSet();
    System.out.println("[table list]");
    List<LocalTable> tableList = new ArrayList<>();
    tableList.add(new LocalTable(0, "整个[" + dbName + "]库", ""));
    while (set.next()) {
      tableList.add(new LocalTable(set.getLong(1), set.getString(2), set.getString(3)));
    }
    for (int i = 0; i < tableList.size(); i++) {
      System.out.println(" " + i + ": " + tableList.get(i).getName());
    }
    set.close();
    stmt.close();
    System.out.println("-----------------------");
    ScannerInput<Integer> si = new ScannerInput<Integer>() {

      @Override
      public String getWelcomeMsg() {
        return "请选择需要导出的表序号：";
      }

      @Override
      public String getErrorMsg() {
        return "---->>表序号输入错误";
      }

      @Override
      public Integer getLegalInput(String input) throws Exception {
        int index = Integer.parseInt(input);
        if (index >= 0 && index < tableList.size()) {
          return index;
        } else {
          throw new Exception();
        }
      }
    };
    tableNameExtract();
    nameTypeSelect();
    
    int indexChoose = si.get();
    if (indexChoose == 0) {
      exportClass(conn, dbName, tableList.subList(1, tableList.size()));
    } else {
      exportClass(conn, dbName, tableList.get(indexChoose));
    }
    return dbName;
  }

  /**
   * 提取表名相关的正则
   * 
   * @summary
   * @author Jail Hu
   * @version v1
   * @since 2016年12月5日 下午7:50:22
   */
  private static void tableNameExtract() {
    tableNamePatt = getInput("请输入对表名进行类名提取的正则表达式（直接回车可跳过此步骤）：");
    if (tableNamePatt != null && !"".equals(tableNamePatt)) {
      Pattern patt = Pattern.compile("(\\(\\S*?\\))");
      Matcher matcher = patt.matcher(tableNamePatt);
      while (matcher.find()) {
        tableNamePattTotal++;
      }
      if (tableNamePattTotal > 0) {
        ScannerInput<Integer> tableNamePattIndexScanner = new ScannerInput<Integer>() {
          @Override
          public Integer getLegalInput(String input) throws Exception {
            int index = Integer.parseInt(input);
            if (index >= 1 && index <= tableNamePattTotal) {
              return index;
            } else {
              throw new Exception();
            }
          }

          @Override
          public String getWelcomeMsg() {
            return "请输入正则表达式提取下标，从[1]开始：";
          }

          @Override
          public String getErrorMsg() {
            return "正则下标输入错误，选择范围 [1] - [" + tableNamePattTotal + "]";
          }
        };
        tableNamePattIndex = tableNamePattIndexScanner.get();
      }
    }
  }

  private static void nameTypeSelect() {
    ScannerInput<Integer> namedTypeScanner = new ScannerInput<Integer>() {
      @Override
      public Integer getLegalInput(String input) throws Exception {
        int index = Integer.parseInt(input);
        return index;
      }

      @Override
      public String getWelcomeMsg() {
        return "请输入您需要的类名格式化方式（0：原始  1：驼峰）：";
      }

      @Override
      public String getErrorMsg() {
        return "类名格式化方式输入错误（0：原始  1：驼峰）";
      }
    };

    namedType = namedTypeScanner.get();

    if (namedType == 1) {
      nameSplit = getInput("请输入您类名的单词分隔符：");
    }
  }

  private static void exportClass(Connection conn, String dbName, LocalTable table) throws SQLException {
    exportClass(conn, dbName, Arrays.asList(table));
  }

  private static void exportClass(Connection conn, String dbName, List<LocalTable> tableList) throws SQLException {
    String packageName = getInput("请输入您类文件的包名：");
    // System.out.println("您输入的类文件包名为：" + packageName + "\n");
    String authorName = getInput("请输入您的大名：");
    // System.out.println("您的大名为：" + authorName + "\n");

    tableList.sort((t1, t2) -> ((Long) t1.getId()).compareTo(t2.getId()));

    String tableIds = tableList.stream().map(t -> String.valueOf(t.getId())).collect(Collectors.joining(","));

    Statement stmt = conn.createStatement();
    stmt.execute(
        "SELECT c.id as tableId, c.name,t.name as colType,c.isnullable,cast(p.value as varchar(500)) as comment,cast(e.text as varchar(500)) as defaultValue FROM "
            + dbName + ".sys.syscolumns as c " + "inner join master.sys.systypes  as t on c.xusertype = t.xusertype " + "left join "
            + dbName + ".sys.extended_properties as p on c.id = p.major_id  and c.colid = p.minor_id left join " + dbName
            + ".sys.syscomments e on c.cdefault=e.id  " + "where c.id in (" + tableIds + ") order by c.id ");
    ResultSet set = stmt.getResultSet();

    int tableIndex = 0;
    LocalTable currentTable = tableList.get(tableIndex);
    System.out.println("开始为您导出java脚本...");

    List<Column> columnList = new ArrayList<>();
    while (set.next()) {
      if (currentTable.getId() == set.getLong(1)) {
        columnList.add(new Column(set.getString(2), set.getString(3), set.getBoolean(4), set.getString(5), set.getString(6)));
      } else {
        try {
          String fileName = ClassGenerator.javaClassGenerate(getFormatedClassName(currentTable.getName()), currentTable, packageName,
              authorName, columnList);
          System.out
              .println("进度： [" + (tableIndex + 1) + "/" + tableList.size() + "] " + currentTable.getName() + " 导出完成 --->" + fileName + ";");
          currentTable = tableList.get(++tableIndex);
          columnList.clear();
        } catch (IOException e) {
          System.err.println(
              "进度： [" + (tableIndex + 1) + "/" + tableList.size() + "] " + currentTable.getName() + " 导出失败，原因【" + e.getMessage() + "】...");
        }
      }
    }
    if (columnList.size() > 0) {
      try {
        String fileName = ClassGenerator.javaClassGenerate(getFormatedClassName(currentTable.getName()), currentTable, packageName,
            authorName, columnList);
        System.out
            .println("进度： [" + (tableIndex + 1) + "/" + tableList.size() + "] " + currentTable.getName() + " 导出完成 --->" + fileName + ";");
      } catch (IOException e) {
        System.err.println(
            "进度： [" + (tableIndex + 1) + "/" + tableList.size() + "] " + currentTable.getName() + " 导出失败，原因【" + e.getMessage() + "】...");
      }
    }
    System.out.println("恭喜，全部导出完毕!");
    set.close();
    stmt.close();
  }

  private static String getFormatedClassName(String tableName) {
    String className = tableName;
    if (tableNamePatt != null && !"".equals(tableNamePatt)) {
      Pattern patt = Pattern.compile(tableNamePatt);
      Matcher matcher = patt.matcher(tableName);
      if (matcher.matches()) {
        className = matcher.group(tableNamePattIndex);
      }
    }
    Objects.nonNull(className);

    if (namedType == 0) {
      return className;
    } else if (namedType == 1) {
      StringBuilder sb = new StringBuilder();
      for (String word : className.split(nameSplit)) {
        sb.append(word.substring(0, 1).toUpperCase());
        sb.append(word.substring(1, word.length()));
      }
      return sb.toString();
    }
    return className;
  }

  private static String getInput(String label) {
    System.out.print(label);
    String str = scanner.nextLine();
    return str;
  }

  @SuppressWarnings("unused")
  private static String getInput() {

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    try {
      String str = br.readLine();
      br.close();
      return str;
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.exit(-1);
    return "";


    /*
     * BufferedInputStream is = new BufferedInputStream(System.in); ByteArrayOutputStream result =
     * new ByteArrayOutputStream(); byte[] buffer = new byte[2]; int length; try { while ((length =
     * is.read(buffer)) != -1) { System.out.println("----" + new String(buffer));
     * result.write(buffer); //当出现读取字节临界时换行符不好处理 if (buffer[length - 2] == 13 && buffer[length - 1]
     * == 10) { is.close(); break; } buffer = new byte[2]; } return result.toString(); } catch
     * (IOException e1) { e1.printStackTrace(); } System.exit(-1); return "";
     */
  }

  static class LocalTable {
    /**
     * 表ID
     */
    private long id;
    /**
     * 表名
     */
    private String name;
    /**
     * 表注释
     */
    private String comment;

    /**
     * 获得 id
     * 
     * @return long
     */
    public long getId() {
      return id;
    }

    /**
     * 设置 id
     * 
     * @param id long
     */
    public void setId(long id) {
      this.id = id;
    }

    /**
     * 获得 name
     * 
     * @return String
     */
    public String getName() {
      return name;
    }

    /**
     * 设置 name
     * 
     * @param name String
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * @return the 表注释
     */
    public String getComment() {
      return this.comment;
    }

    /**
     * 设置 表注释
     * 
     * @param String to set
     */
    public void setComment(String comment) {
      this.comment = comment;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
      return "Table [id=" + id + ", name=" + name + "]";
    }

    public LocalTable() {}

    public LocalTable(long id, String name, String comment) {
      this.id = id;
      this.name = name;
      this.comment = comment;
    }
  }

  public static class Column {
    private String name;
    private String colType;
    private boolean nullable;
    private String comment;
    private String defaultValue;

    /**
     * 获得 name
     * 
     * @return String
     */
    public String getName() {
      return name;
    }

    /**
     * 设置 name
     * 
     * @param name String
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * 获得 colType
     * 
     * @return String
     */
    public String getColType() {
      return colType;
    }

    /**
     * 设置 colType
     * 
     * @param colType String
     */
    public void setColType(String colType) {
      this.colType = colType;
    }

    /**
     * 获得 nullable
     * 
     * @return boolean
     */
    public boolean isNullable() {
      return nullable;
    }

    /**
     * 设置 nullable
     * 
     * @param nullable boolean
     */
    public void setNullable(boolean nullable) {
      this.nullable = nullable;
    }

    /**
     * 获得 comment
     * 
     * @return String
     */
    public String getComment() {
      return comment;
    }

    /**
     * 设置 comment
     * 
     * @param comment String
     */
    public void setComment(String comment) {
      this.comment = comment;
    }

    /**
     * 获得 defaultValue
     * 
     * @return String
     */
    public String getDefaultValue() {
      return defaultValue;
    }

    /**
     * 设置 defaultValue
     * 
     * @param defaultValue String
     */
    public void setDefaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
      return "LocalColumn [name=" + name + ", colType=" + colType + ", nullable=" + nullable + ", comment=" + comment + ", defaultValue="
          + defaultValue + "]";
    }

    public Column() {
      // TODO Auto-generated constructor stub
    }

    public Column(String name, String colType, boolean nullable, String comment, String defaultValue) {
      this.name = name;
      this.colType = colType;
      this.nullable = nullable;
      this.comment = comment;
      this.defaultValue = defaultValue;
    }
  }
}
