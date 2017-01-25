/*
 * Copyright 2017 Jonas Fonseca
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jonas.circe.config

import cats.data.ValidatedNel
import io.circe._
import java.io.File
import scala.util.{ Failure, Success, Try }
import scala.collection.JavaConverters._
import com.typesafe.config._

object parser extends Parser {

  private[this] final def toJson[T](parseConfig: => Config): Either[ParsingFailure, Json] = {
    def convertValueUnsafe(value: ConfigValue): Json = value match {
      case obj: ConfigObject =>
        Json.fromFields(obj.asScala.mapValues(convertValueUnsafe))

      case list: ConfigList =>
        Json.fromValues(list.asScala.map(convertValueUnsafe))

      case scalar =>
        (value.valueType, value.unwrapped) match {
          case (ConfigValueType.NULL, _) =>
            Json.Null
          case (ConfigValueType.NUMBER, _) =>
            JsonNumber.fromString(value.render).map(Json.fromJsonNumber).getOrElse {
              throw new NumberFormatException(s"Invalid numeric string ${value.render}")
            }
          case (ConfigValueType.BOOLEAN, boolean: java.lang.Boolean) =>
            Json.fromBoolean(boolean)
          case (ConfigValueType.STRING, str: String) =>
            Json.fromString(str)

          case (valueType, _) =>
            throw new RuntimeException(s"No conversion for $valueType with value $value")
        }
    }

    Try(parseConfig.root).map(convertValueUnsafe) match {
      case Success(json)  => Right(json)
      case Failure(error) => println(error); Left(ParsingFailure(error.getMessage, error))
    }
  }

  final def parse(config: Config): Either[ParsingFailure, Json] =
    toJson(config)

  final def parse(input: String): Either[ParsingFailure, Json] =
    toJson(ConfigFactory.parseString(input))

  final def parseFile(file: File): Either[ParsingFailure, Json] =
    toJson(ConfigFactory.parseFile(file))

  final def decode[A: Decoder](config: Config): Either[Error, A] =
    finishDecode(parse(config))

  final def decodeFile[A: Decoder](file: File): Either[Error, A] =
    finishDecode[A](parseFile(file))

  final def decodeFileAccumulating[A: Decoder](file: File): ValidatedNel[Error, A] =
    finishDecodeAccumulating[A](parseFile(file))

}