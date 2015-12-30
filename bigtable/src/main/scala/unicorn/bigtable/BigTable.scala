/*******************************************************************************
 * (C) Copyright 2015 ADP, LLC.
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package unicorn.bigtable

import java.util.Date
import unicorn.util._

/** Key of Cell */
case class Key(row: ByteArray, family: String, qualifier: ByteArray, timestamp: Long)
/** Cell in wide columnar table */
case class Cell(row: ByteArray, family: String, qualifier: ByteArray, value: ByteArray, timestamp: Long = 0)
/** A column of a column family */
case class Column(qualifier: ByteArray, value: ByteArray, timestamp: Long = 0)
/** A column family */
case class ColumnFamily(family: String, columns: Seq[Column])
/** A Row */
case class Row(row: ByteArray, families: Seq[ColumnFamily])

/**
 * Abstraction of wide columnar data table.
 *
 * @author Haifeng Li
 */
trait BigTable extends AutoCloseable {
  val name: String

  val columnFamilies: Seq[String]

  override def toString = name + columnFamilies.mkString("[", ",", "]")
  override def hashCode = toString.hashCode

  /**
   * Get a value.
   */
  def apply(row: ByteArray, family: String, column: ByteArray): Option[ByteArray] = {
    val seq = get(row, family, column)
    if (seq.isEmpty) None else Some(seq.head.value)
  }

  /**
   * Update a value. With it, one may use the syntactic sugar
   * table(row, family, column) = value
   */
  def update(row: ByteArray, family: String, column: ByteArray, value: ByteArray): Unit = {
    put(row, family, column, value)
  }

  /**
   * Get one or more columns of a column family. If columns is empty, get all columns in the column family.
   */
  def get(row: ByteArray, family: String, columns: ByteArray*): Seq[Column]

  /**
   * Get all columns in one or more column families. If families is empty, get all column families.
   */
  def get(row: ByteArray, families: Seq[(String, Seq[ByteArray])] = Seq.empty): Seq[ColumnFamily]

  /**
   * Get multiple rows for given columns. If columns is empty, get all columns of the column family.
   * The implementation may or may not optimize the batch operations.
   * In particular, Accumulo does optimize it.
   */
  def getBatch(rows: Seq[ByteArray], family: String, columns: ByteArray*): Seq[Row]

  /**
   * Get multiple rows for given column families. If families is empty, get all column families.
   * The implementation may or may not optimize the batch operations.
   * In particular, Accumulo does optimize it.
   */
  def getBatch(rows: Seq[ByteArray], families: Seq[(String, Seq[ByteArray])] = Seq.empty): Seq[Row]

  /**
   * Upsert a value.
   */
  def put(row: ByteArray, family: String, column: ByteArray, value: ByteArray, timestamp: Long = 0L): Unit

  /**
   * Upsert values.
   */
  def put(row: ByteArray, family: String, columns: Column*): Unit

  /**
   * Upsert values.
   */
  def put(row: ByteArray, families: ColumnFamily*): Unit

  /**
   * Update the values of one or more rows.
   * The implementation may or may not optimize the batch operations.
   * In particular, Accumulo does optimize it.
   */
  def putBatch(rows: Row*): Unit

  /**
   * Delete the columns of a row. If columns is empty, delete all columns in the family.
   */
  def delete(row: ByteArray, family: String, columns: ByteArray*): Unit

  /**
   * Delete the columns of a row. If families is empty, delete the whole row.
   */
  def delete(row: ByteArray, families: Seq[String] = Seq.empty): Unit

  /**
   * Delete multiple rows.
   * The implementation may or may not optimize the batch operations.
   * In particular, Accumulo does optimize it.
   */
  def deleteBatch(rows: Seq[ByteArray], family: String, columns: ByteArray*): Unit

  /**
   * Delete multiple rows.
   * The implementation may or may not optimize the batch operations.
   * In particular, Accumulo does optimize it.
   */
  def deleteBatch(rows: Seq[ByteArray], families: Seq[String] = Seq.empty): Unit
}

trait TimeTravel {
  /**
   * Get one or more columns of a column family. If columns is empty, get all columns in the column family.
   */
  def getAsOf(asOfDate: Date, row: ByteArray, family: String, columns: ByteArray*): Seq[Column]

  /**
   * Get all columns in one or more column families. If families is empty, get all column families.
   */
  def getAsOf(asOfDate: Date, row: ByteArray, families: Seq[(String, Seq[ByteArray])] = Seq.empty): Seq[ColumnFamily]

}

/** Row scan iterator */
trait RowScanner extends Iterator[Row] {
  def close: Unit
}

trait ScanBase {
  /** Frist row in a table. */
  val startRowKey: ByteArray
  /** Last row in a table. */
  val endRowKey: ByteArray


  /**
   * When scanning for a prefix the scan should stop immediately after the the last row that
   * has the specified prefix. This method calculates the closest next row key immediately following
   * the given prefix.
   * <p>
   * To scan rows with a given prefix, do
   * <pre>
   * {@code
   * scan(prefix, nextRowKeyForPrefix(prefix))
   * }
   * </pre>
   *
   * @param prefix the row key prefix.
   * @return the closest next row key immediately following the given prefix.
   */
  def nextRowKeyForPrefix(prefix: Array[Byte]): Array[Byte] = {
    val ff:  Byte = 0xFF.toByte
    val one: Byte = 1

    // Essentially we are treating it like an 'unsigned very very long' and doing +1 manually.
    // Search for the place where the trailing 0xFFs start
    val offset = prefix.reverse.indexOf(ff) match {
      case -1 => prefix.length
      case  x => prefix.length - x - 1
    }

    // We got an 0xFFFF... (only FFs) stopRow value which is
    // the last possible prefix before the end of the table.
    // So set it to stop at the 'end of the table'
    if (offset == 0) {
      return endRowKey
    }

    // Copy the right length of the original
    val stopRow = java.util.Arrays.copyOfRange(prefix, 0, offset)
    // And increment the last one
    stopRow(stopRow.length - 1) = (stopRow(stopRow.length - 1) + one).toByte
    stopRow
  }
}

/** If BigTable supports row scan. */
trait RowScan extends ScanBase {
  /**
   * Scan the range for all columns in one or more column families. If families is empty, get all column families.
   * @param startRow row to start scanner at or after (inclusive)
   * @param stopRow row to stop scanner before (exclusive)
   */
  def scan(startRow: ByteArray, stopRow: ByteArray, families: Seq[(String, Seq[ByteArray])] = Seq.empty): RowScanner

  /**
   * Scan one or more columns. If columns is empty, get all columns in the column family.
   * @param startRow row to start scanner at or after (inclusive)
   * @param stopRow row to stop scanner before (exclusive)
   */
  def scan(startRow: ByteArray, stopRow: ByteArray, family: String, columns: ByteArray*): RowScanner

  /**
   * Scan the whole table.
   */
  def scanAll(families: Seq[(String, Seq[ByteArray])] = Seq.empty): RowScanner = {
    scan(startRowKey, endRowKey, families)
  }

  /**
   * Scan the whole table.
   */
  def scanAll(family: String, columns: ByteArray*): RowScanner = {
    scan(startRowKey, endRowKey, family, columns: _*)
  }

  /**
   * Scan the rows whose key starts with the given prefix.
   */
  def scanPrefix(prefix: ByteArray, families: Seq[(String, Seq[ByteArray])] = Seq.empty): RowScanner = {
    scan(prefix, nextRowKeyForPrefix(prefix), families)
  }

  /**
   * Scan the rows whose key starts with the given prefix.
   */
  def scanPrefix(prefix: ByteArray, family: String, columns: ByteArray*): RowScanner = {
    scan(prefix, nextRowKeyForPrefix(prefix), family, columns: _*)
  }
}

/** Intra-row scan iterator */
trait IntraRowScanner extends Iterator[Column] {
  def close: Unit
}

/** If BigTable supports intra-row scan. */
trait IntraRowScan {
  /**
   * Scan a column range for a given row.
   * @param startColumn column to start scanner at or after (inclusive)
   * @param stopColumn column to stop scanner before or at (inclusive)
   */
  def intraRowScan(row: ByteArray, family: String, startColumn: ByteArray, stopColumn: ByteArray): IntraRowScanner
}

object ScanFilter {
  object CompareOperator extends Enumeration {
    type CompareOperator = Value
    val Equal, NotEqual, Greater, GreaterOrEqual, Less, LessOrEqual = Value
  }

  import CompareOperator._
  sealed trait Expression
  case class And(left: Expression, right: Expression) extends Expression
  case class Or (left: Expression, right: Expression) extends Expression
  case class BasicExpression(op: CompareOperator, family: String, column: ByteArray, value: ByteArray) extends Expression
}

/** If BigTable supports filter. */
trait FilterScan extends ScanBase {
  /**
   * Scan the range for all columns in one or more column families. If families is empty, get all column families.
   * @param startRow row to start scanner at or after (inclusive)
   * @param stopRow row to stop scanner before (exclusive)
   * @param filter filter expression
   */
  def filterScan(filter: ScanFilter.Expression, startRow: ByteArray, stopRow: ByteArray, families: Seq[(String, Seq[ByteArray])] = Seq.empty): RowScanner

  /**
   * Scan one or more columns. If columns is empty, get all columns in the column family.
   * @param startRow row to start scanner at or after (inclusive)
   * @param stopRow row to stop scanner before (exclusive)
   * @param filter filter expression
   */
  def filterScan(filter: ScanFilter.Expression, startRow: ByteArray, stopRow: ByteArray, family: String, columns: ByteArray*): RowScanner

  /**
   * Get the range for all columns in one or more column families. If families is empty, get all column families.
   * @param filter filter expression
   */
  def filterGet(filter: ScanFilter.Expression, row: ByteArray, families: Seq[(String, Seq[ByteArray])] = Seq.empty): Option[Seq[ColumnFamily]]

  /**
   * Get one or more columns. If columns is empty, get all columns in the column family.
   * @param filter filter expression
   */
  def filterGet(filter: ScanFilter.Expression, row: ByteArray, family: String, columns: ByteArray*): Option[Seq[Column]]

  /**
   * Scan the whole table.
   */
  def filterScanAll(filter: ScanFilter.Expression, families: Seq[(String, Seq[ByteArray])] = Seq.empty): RowScanner = {
    filterScan(filter, startRowKey, endRowKey, families)
  }

  /**
   * Scan the whole table.
   */
  def filterScanAll(filter: ScanFilter.Expression, family: String, columns: ByteArray*): RowScanner = {
    filterScan(filter, startRowKey, endRowKey, family, columns: _*)
  }

  /**
   * Scan the rows whose key starts with the given prefix.
   */
  def filterScanPrefix(filter: ScanFilter.Expression, prefix: ByteArray, families: Seq[(String, Seq[ByteArray])] = Seq.empty): RowScanner = {
    filterScan(filter, prefix, nextRowKeyForPrefix(prefix), families)
  }

  /**
   * Scan the rows whose key starts with the given prefix.
   */
  def filterScanPrefix(filter: ScanFilter.Expression, prefix: ByteArray, family: String, columns: ByteArray*): RowScanner = {
    filterScan(filter, prefix, nextRowKeyForPrefix(prefix), family, columns: _*)
  }
}


/** If BigTable supports cell level security. */
trait CellLevelSecurity {
  /**
   * Visibility expression which can be associated with a cell.
   * When it is set with a Mutation, all the cells in that mutation will get associated with this expression.
   */
  def setCellVisibility(expression: String): Unit

  /**
   * Returns the current visibility expression setting.
   */
  def getCellVisibility: String

  /**
   * Visibility labels associated with a Scan/Get deciding which all labeled data current scan/get can access.
   */
  def setAuthorizations(labels: String*): Unit

  /**
   * Returns the current authorization labels.
   */
  def getAuthorizations: Seq[String]
}

/** If BigTable supports rollback to previous version of a cell. */
trait Rollback {
  /**
   * Rollback to the previous version for the given column of a row.
   */
  def rollback(row: ByteArray, family: String, columns: ByteArray*): Unit
}

/** If BigTable supports appending to a cell. */
trait Appendable {
  /**
   * Append the value of a column.
   */
  def append(row: ByteArray, family: String, column: ByteArray, value: ByteArray): Unit
}

/** If BigTable supports counter data type. */
trait Counter {
  /** Get the value of a counter column */
  def getCounter(row: ByteArray, family: String, column: ByteArray): Long

  /**
   * Add to a counter column.
   */
  def addCounter(row: ByteArray, family: String, column: ByteArray, value: Long): Unit
}
