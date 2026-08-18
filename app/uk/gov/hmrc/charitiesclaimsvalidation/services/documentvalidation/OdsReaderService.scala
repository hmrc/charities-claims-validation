/*
 * Copyright 2025 HM Revenue & Customs
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
 */

package uk.gov.hmrc.charitiesclaimsvalidation.services.documentvalidation

import cats.effect.*
import org.w3c.dom.{Document, Element, Node}
import play.api.Logger
import uk.gov.hmrc.charitiesclaimsvalidation.models.domain.errors.{BadSheetNameException, NotAnOdsFileException}
import uk.gov.hmrc.charitiesclaimsvalidation.models.validation.*

import java.math.RoundingMode
import java.net.URI
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.{ZipFile, ZipInputStream}
import javax.xml.parsers.DocumentBuilderFactory
import scala.util.Try

object OdsReaderService:

  private val logger = Logger(getClass)

  def withDocumentStream[A](downloadUrl: String)(f: Document => IO[A]): IO[A] =
    Resource
      .fromAutoCloseable(
        IO.blocking(new URI(downloadUrl).toURL.openStream())
      )
      .use { networkStream =>
        Resource
          .fromAutoCloseable(
            IO.blocking(new ZipInputStream(networkStream))
          )
          .use { zin =>
            for {
              doc <- extractAndParseDocument(zin)
              a   <- f(doc)
            } yield a
          }
      }

  def withDocument[A](path: String)(f: Document => IO[A]): IO[A] =
    Resource
      .fromAutoCloseable(IO.blocking(new ZipFile(Paths.get(path).toFile)))
      .use { zip =>
        for {
          doc <- extractAndParseDocument(zip)
          a   <- f(doc)
        } yield a
      }

  def rowsFromDocument[A <: DocumentRow: DocumentRowDecoder](
    doc: Document,
    layout: SheetLayout
  ): IO[List[A]] =
    rowsFromDocumentWithIndex(doc, layout).map(_.map(_._2))

  def rowsFromDocumentWithIndex[A <: DocumentRow: DocumentRowDecoder](
    doc: Document,
    layout: SheetLayout
  ): IO[List[(Int, A)]] = IO {

    val rows = extractRows(doc, layout.rowRange.max)

    layout.rowRange.toList.flatMap { rowIndex =>
      rows.lift(rowIndex) match {
        case None =>
          Nil

        case Some(rowElement) =>
          val allCells = extractCells(rowElement, layout.cellRange.max)

          val selectedCells: List[String] =
            layout.cellRange.toList.zipWithIndex.map { case (cellIndex, columnIndex) =>
              allCells
                .lift(cellIndex)
                .map(cell => extractCellValue(cell, layout.columnFormats.getOrElse(columnIndex, CellFormat.Text)))
                .getOrElse("")
            }

          summon[DocumentRowDecoder[A]]
            .fromCells(selectedCells)
            .toList
            .map(row => rowIndex -> row)
      }
    }
  }

  private def extractRows(doc: Document, maxRows: Int): Vector[Element] = {
    val tables = doc.getElementsByTagName("table:table")
    if (tables.getLength == 0)
      Vector.empty
    else {
      val table    = tables.item(0).asInstanceOf[Element]
      val children = table.getChildNodes

      (0 until children.getLength).iterator
        .flatMap { i =>
          children.item(i) match {
            case row: Element if row.getNodeName == "table:table-row" =>
              val repeat = row.getAttribute("table:number-rows-repeated").toIntOption.getOrElse(1)
              Iterator.fill(repeat)(row)
            case _ =>
              Iterator.empty
          }
        }
        .take(maxRows)
        .toVector
    }
  }

  private def extractCells(row: Element, maxColumns: Int): Vector[Element] = {
    val cells = row.getElementsByTagName("table:table-cell")

    (0 until cells.getLength).iterator
      .flatMap { i =>
        val cell   = cells.item(i).asInstanceOf[Element]
        val repeat = cell.getAttribute("table:number-columns-repeated").toIntOption.filter(_ >= 1).getOrElse(1)
        Iterator.fill(repeat)(cell)
      }
      .take(maxColumns)
      .toVector
  }

  private def extractCellValue(cell: Element, format: CellFormat): String =
    format match {
      case CellFormat.Money =>
        roundedToPence(cell.getAttribute("office:value")).getOrElse(extractCellText(cell))
      case CellFormat.Date =>
        storedDate(cell.getAttribute("office:date-value")).getOrElse(extractCellText(cell))
      case CellFormat.Text =>
        extractCellText(cell)
    }

  private val maxIntegerDigits = 20

  private def roundedToPence(storedValue: String): Option[String] =
    Try(BigDecimal(storedValue)).toOption
      .filter(value => value.precision - value.scale <= maxIntegerDigits)
      .map(_.bigDecimal.setScale(2, RoundingMode.HALF_EVEN).toPlainString)

  private val isoDateLength      = 10
  private val scheduleDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")

  private def storedDate(storedValue: String): Option[String] =
    Try(LocalDate.parse(storedValue.take(isoDateLength))).toOption
      .map(_.format(scheduleDateFormat))

  private def extractCellText(cell: Element): String = {
    val paragraphs = cell.getElementsByTagName("text:p")

    (0 until paragraphs.getLength)
      .map(i => traverseTextNodes(paragraphs.item(i)))
      .mkString("\n")
      .trim
  }

  private def traverseTextNodes(node: Node): String = {
    node.getNodeType match {
      case Node.TEXT_NODE =>
        node.getTextContent
      case Node.ELEMENT_NODE =>
        node.getNodeName match {
          case "text:p" | "text:span" =>
            childNodes(node).map(traverseTextNodes).mkString
          case "text:s" =>
            val count = node.asInstanceOf[Element].getAttribute("text:c").toIntOption.getOrElse(1)
            " " * count
          case "text:c" =>
            val count = node.asInstanceOf[Element].getAttribute("text:c").toIntOption.getOrElse(1)
            node.getTextContent * count
          case _ =>
            childNodes(node).map(traverseTextNodes).mkString
        }
      case _ =>
        ""
    }
  }

  private def childNodes(node: Node): IndexedSeq[Node] =
    val children = node.getChildNodes
    (0 until children.getLength).map(children.item)

  def cellFromDocument(doc: Document, sheetCell: SheetCell): IO[String] = IO {
    val rows = extractRows(doc, sheetCell.rowIndex + 1)

    (for {
      rowElement <- rows.lift(sheetCell.rowIndex)
      cells = rowElement.getElementsByTagName("table:table-cell")
      cell <- Option(cells.item(sheetCell.cellIndex)).map(_.asInstanceOf[Element])
    } yield extractCellValue(cell, sheetCell.format)).getOrElse("")
  }

  private def extractAndParseDocument(zip: ZipFile): IO[Document] = {
    IO.blocking {
      val entry = zip.getEntry("content.xml")
      if (entry == null) {
        logger.warn("content.xml not found in ODS ZipFile")
        throw NotAnOdsFileException()
      }
      val input = zip.getInputStream(entry)
      val doc   = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)

      doc.getDocumentElement.normalize()
      doc
    }
  }

  private def extractAndParseDocument(zin: ZipInputStream): IO[Document] =
    IO.blocking {
      var entry = zin.getNextEntry

      while (entry != null && entry.getName != "content.xml")
        entry = zin.getNextEntry

      if (entry == null) {
        logger.warn("content.xml not found in ODS ZipInputStream")
        throw NotAnOdsFileException()
      }

      val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
      val doc     = builder.parse(zin)

      doc.getDocumentElement.normalize()
      doc
    }

  def extractSheetName(doc: Document): IO[String] = IO {
    val tables = doc.getElementsByTagName("table:table")

    if (tables.getLength > 0) {
      val firstTable = tables.item(0).asInstanceOf[Element]
      val sheetName  = firstTable.getAttribute("table:name")

      if (sheetName.nonEmpty) {
        sheetName
      } else {
        logger.warn("First table element found but it has no name attribute")
        throw BadSheetNameException()
      }
    } else {
      logger.warn("No sheets (table:table) found in the ODS document")
      throw BadSheetNameException()
    }
  }
