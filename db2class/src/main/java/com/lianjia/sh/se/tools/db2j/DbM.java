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
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DbM {

  static Scanner scanner = new Scanner(System.in);

  public static void main(String[] args) throws SQLException {
    System.out.println("—————————————————————————————————————————————————\n");
    System.out.println("　　　　　【上海链家SE】数据库对象导出工具 Ver1.1.0");
    System.out.println("                        Author:Jail Hu\n");
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

    ScannerInput si = new ScannerInput() {

      @Override
      public int getInputIndex(String input) throws NumberFormatException {
        return Integer.parseInt(input);
      }

      @Override
      public int getLegalIndex(int index) throws Exception {
        if (dbMap.containsKey(index)) {
          return index;
        } else {
          throw new Exception();
        }
      }

      @Override
      public int getRetryCount() {
        return 3;
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
    String dbName = dbMap.get(chooseSource.apply(si));
    System.out.println("您选择的数据库为：" + dbName + "\n");
    return dbName;
  }

  static Function<ScannerInput, Integer> chooseSource = new Function<ScannerInput, Integer>() {

    @Override
    public Integer apply(ScannerInput t) {
      return this.get(t, 0);
    }

    private Integer get(ScannerInput t, int i) {
      String input = getInput(t.getWelcomeMsg());
      try {
        int index = t.getInputIndex(input);
        return t.getLegalIndex(index);
      } catch (Exception e) {
        if (i < t.getRetryCount()) {
          System.err.println(t.getErrorMsg() + " 请重试 " + (i + 1) + "/" + t.getRetryCount() + "\n");
          i++;
          return get(t, i);
        } else {
          throw new NumberFormatException((t.getRetryCount() > 0 ? "重试结束，" : "") + "参数输入有误，程序异常跳出");
        }
      }
    }
  };

  static interface ScannerInput {

    int getRetryCount();

    int getInputIndex(String input) throws NumberFormatException;

    int getLegalIndex(int index) throws Exception;

    String getWelcomeMsg();

    String getErrorMsg();
  }

  private static String tableOperate(Connection conn, String dbName) throws SQLException {
    Statement stmt = conn.createStatement();
    stmt.execute("SELECT id,name FROM " + dbName + ".sys.SysObjects Where XType='U' ORDER BY Name");
    ResultSet set = stmt.getResultSet();
    System.out.println("[table list]");
    List<LocalTable> tableList = new ArrayList<>();
    tableList.add(new LocalTable(0, "整个[" + dbName + "]库"));
    while (set.next()) {
      tableList.add(new LocalTable(set.getLong(1), set.getString(2)));
    }
    for (int i = 0; i < tableList.size(); i++) {
      System.out.println(" " + i + ": " + tableList.get(i).getName());
    }
    set.close();
    stmt.close();
    System.out.println("-----------------------");
    ScannerInput si = new ScannerInput() {

      @Override
      public String getWelcomeMsg() {
        return "请选择需要导出的表序号：";
      }

      @Override
      public int getRetryCount() {
        return 3;
      }

      @Override
      public int getInputIndex(String input) throws NumberFormatException {
        return Integer.parseInt(input);
      }

      @Override
      public String getErrorMsg() {
        return "---->>表序号输入错误";
      }

      @Override
      public int getLegalIndex(int index) throws Exception {
        if (index >= 0 && index < tableList.size()) {
          return index;
        } else {
          throw new Exception();
        }
      }
    };
    int indexChoose = chooseSource.apply(si);
    if (indexChoose == 0) {
      exportClass(conn, dbName, tableList.subList(1, tableList.size()));
    } else {
      exportClass(conn, dbName, tableList.get(indexChoose));
    }
    return dbName;
  }

  private static void exportClass(Connection conn, String dbName, LocalTable table) throws SQLException {
    exportClass(conn, dbName, Arrays.asList(table));
  }

  private static void exportClass(Connection conn, String dbName, List<LocalTable> tableList) throws SQLException {
    String packageName = getInput("请输入您类文件的包名：");
    System.out.println("您输入的类文件包名为：" + packageName + "\n");
    String authorName = getInput("请输入您的大名：");
    System.out.println("您的大名为：" + authorName + "\n");

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
          String fileName = ClassGenerator.javaClassGenerate(currentTable.getName(), packageName, authorName, columnList);
          currentTable = tableList.get(++tableIndex);
          System.out.println("进度： [" + tableIndex + "/" + tableList.size() + "] " + currentTable.getName() + " 导出完成 --->" + fileName + ";");
          columnList.clear();
        } catch (IOException e) {
          System.err.println(
              "进度： [" + (tableIndex + 1) + "/" + tableList.size() + "] " + currentTable.getName() + " 导出失败，原因【" + e.getMessage() + "】...");
        }
      }
    }
    if (columnList.size() > 0) {
      try {
        String fileName = ClassGenerator.javaClassGenerate(currentTable.getName(), packageName, authorName, columnList);
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

  private static String getInput(String label) {
    System.out.print(label);
    String str = scanner.next();
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

    public LocalTable(long id, String name) {
      this.id = id;
      this.name = name;
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
