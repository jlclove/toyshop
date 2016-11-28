package com.lianjia.sh.se.tools.db2j.adaptor;

public class JavaAdaptor {

  /**
   * 是否含有 Date字段
   * 
   * @param mssqlType
   * @return
   * @summary
   * @author Jail Hu
   * @version v1
   * @since 2016年11月27日 下午8:07:45
   */
  public static boolean hasMSSQLDate(String mssqlType) {
    switch (mssqlType.toLowerCase()) {
      case "date":
      case "datetime":
      case "datetime2":
      case "smalldatetime":
        return true;
      default:
        return false;
    }
  }

  /**
   * 是否含有大精度字段
   * 
   * @param mssqlType
   * @return
   * @summary
   * @author Jail Hu
   * @version v1
   * @since 2016年11月27日 下午8:08:01
   */
  public static boolean hasMSSQLBigPrecision(String mssqlType) {
    switch (mssqlType.toLowerCase()) {
      case "money":
      case "decimal":
      case "numeric":
        return true;
      default:
        return false;
    }
  }

  public static boolean hasMSSQLBoolean(String mssqlType) {
    switch (mssqlType.toLowerCase()) {
      case "bit":
        return true;
      default:
        return false;
    }
  }

  public static String typeFromMSSQL(String mssqlType, boolean nullable) {
    switch (mssqlType.toLowerCase()) {
      case "varchar":
      case "nvarchar":
      case "char":
      case "text":
      case "ntext":
      case "nchar":
        return "String";
      case "int":
      case "smallint":
        return nullable ? "Integer" : "int";
      case "bigint":
        return nullable ? "Long" : "long";
      case "float":
        return nullable ? "Double" : "double";
      case "real":
        return nullable ? "Float" : "float";
      case "tinyint":// 需要注意符号问题
        return nullable ? "Byte" : "byte";
      case "binary":
      case "varbinary":
      case "image":
        return nullable ? "Byte[]" : "byte[]";
      default:
        if (hasMSSQLDate(mssqlType)) {
          return nullable ? "Date" : "Date";
        }
        if (hasMSSQLBoolean(mssqlType)) {
          return nullable ? "Boolean" : "boolean";
        }
        if (hasMSSQLBigPrecision(mssqlType)) {
          return "BigDecimal";
        }
        throw new IllegalArgumentException("字段类型【" + mssqlType + "】转换异常");
    }
  }
}
