/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
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
package org.neo4j.cypher.internal.ast

import org.neo4j.cypher.internal.ast.ASTSlicingPhrase.checkExpressionIsStaticInt
import org.neo4j.cypher.internal.ast.Match.hintPrettifier
import org.neo4j.cypher.internal.ast.ReturnItems.ReturnVariables
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour.OnErrorFail
import org.neo4j.cypher.internal.ast.connectedComponents.RichConnectedComponent
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.ast.prettifier.Prettifier
import org.neo4j.cypher.internal.ast.semantics.Scope
import org.neo4j.cypher.internal.ast.semantics.SemanticAnalysisTooling
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.fromFunction
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.success
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.when
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckResult
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckable
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticErrorDef
import org.neo4j.cypher.internal.ast.semantics.SemanticExpressionCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticExpressionCheck.FilteringExpressions
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticPatternCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticPatternCheck.error
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.ast.semantics.TypeGenerator
import org.neo4j.cypher.internal.ast.semantics.iterableOnceSemanticChecking
import org.neo4j.cypher.internal.ast.semantics.optionSemanticChecking
import org.neo4j.cypher.internal.expressions.And
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.Contains
import org.neo4j.cypher.internal.expressions.EndsWith
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.Expression.SemanticContext
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.HasLabels
import org.neo4j.cypher.internal.expressions.HasTypes
import org.neo4j.cypher.internal.expressions.In
import org.neo4j.cypher.internal.expressions.InequalityExpression
import org.neo4j.cypher.internal.expressions.IsNotNull
import org.neo4j.cypher.internal.expressions.LabelOrRelTypeName
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.MapExpression
import org.neo4j.cypher.internal.expressions.MatchMode.DifferentRelationships
import org.neo4j.cypher.internal.expressions.MatchMode.MatchMode
import org.neo4j.cypher.internal.expressions.MatchMode.RepeatableElements
import org.neo4j.cypher.internal.expressions.Namespace
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.NonPrefixedPatternPart
import org.neo4j.cypher.internal.expressions.Not
import org.neo4j.cypher.internal.expressions.Or
import org.neo4j.cypher.internal.expressions.Ors
import org.neo4j.cypher.internal.expressions.Parameter
import org.neo4j.cypher.internal.expressions.ParenthesizedPath
import org.neo4j.cypher.internal.expressions.PathConcatenation
import org.neo4j.cypher.internal.expressions.PathPatternPart
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternElement
import org.neo4j.cypher.internal.expressions.PatternPart
import org.neo4j.cypher.internal.expressions.ProcedureName
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.QuantifiedPath
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.SimplePattern
import org.neo4j.cypher.internal.expressions.StartsWith
import org.neo4j.cypher.internal.expressions.StringLiteral
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.containsAggregate
import org.neo4j.cypher.internal.expressions.functions.Function.isIdFunction
import org.neo4j.cypher.internal.label_expressions.LabelExpression
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Disjunctions
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Leaf
import org.neo4j.cypher.internal.label_expressions.LabelExpressionPredicate
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.CartesianProductNotification
import org.neo4j.cypher.internal.util.Foldable.FoldableAny
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.helpers.StringHelper.RichString
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTBoolean
import org.neo4j.cypher.internal.util.symbols.CTDateTime
import org.neo4j.cypher.internal.util.symbols.CTDuration
import org.neo4j.cypher.internal.util.symbols.CTFloat
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTList
import org.neo4j.cypher.internal.util.symbols.CTMap
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.symbols.CTPath
import org.neo4j.cypher.internal.util.symbols.CTRelationship
import org.neo4j.cypher.internal.util.symbols.CTString
import org.neo4j.cypher.internal.util.symbols.CypherType

import scala.annotation.tailrec

sealed trait Clause extends ASTNode with SemanticCheckable with SemanticAnalysisTooling {
  def name: String

  def returnVariables: ReturnVariables = ReturnVariables.empty

  case class LabelExpressionsPartition(
    legacy: Set[LabelExpression] = Set.empty,
    gpm: Set[LabelExpression] = Set.empty,
    leaf: Set[LabelExpression] = Set.empty
  )

  final override def semanticCheck: SemanticCheck =
    clauseSpecificSemanticCheck chain
      when(shouldRunGpmChecks) {
        fromFunction(checkIfMixingLabelExpressionWithOldSyntax) chain
          checkIfMixingLegacyVarLengthWithQPPs
      }

  protected def shouldRunGpmChecks: Boolean = true

  private val stringifier = ExpressionStringifier()

  object SetExtractor {
    def unapplySeq[T](s: Set[T]): Option[Seq[T]] = Some(s.toSeq)
  }

  private def checkIfMixingLabelExpressionWithOldSyntax(state: SemanticState): SemanticCheckResult = {
    val partition = this.folder.treeFold(LabelExpressionsPartition()) {
      case NodePattern(_, Some(le), _, _) => acc =>
          TraverseChildren(sortLabelExpressionIntoPartition(
            le,
            isNode = true,
            acc
          ))
      case LabelExpressionPredicate(entity, le) => acc =>
          SkipChildren(sortLabelExpressionIntoPartition(
            le,
            isNode = state.expressionType(entity).specified == CTNode.invariant,
            acc
          ))
      case RelationshipPattern(_, Some(le), _, _, _, _) => acc =>
          TraverseChildren(sortLabelExpressionIntoPartition(
            le,
            isNode = false,
            acc
          ))
      case LabelExpression => throw new IllegalStateException("Missing a case for label expression location")
    }

    val containsIs = (partition.gpm ++ partition.legacy ++ partition.leaf).exists(le => le.containsIs)

    when(partition.gpm.nonEmpty || containsIs) {
      // we prefer the new way, so we will only error on the "legacy" expressions
      val maybeExplanation = partition.legacy.map { le =>
        (stringifier.stringifyLabelExpression(le.replaceColonSyntax), le.containsIs, le.position)
      } match {
        case SetExtractor() => None
        case SetExtractor((singleExpression, containsIs, pos)) =>
          val isOrColon = if (containsIs) "IS " else ":"
          Some((s"This expression could be expressed as $isOrColon$singleExpression.", pos))
        // we report all error on the first position as we will later on throw away everything but the first error.
        case set: Set[(String, Boolean, InputPosition)] =>
          val replacement = set.map(x => (if (x._2) "IS " else ":") + x._1)
          Some((s"These expressions could be expressed as ${replacement.mkString(", ")}.", set.head._3))
      }
      maybeExplanation match {
        case Some((explanation, pos)) => SemanticError(
            if (partition.gpm.nonEmpty)
              s"Mixing label expression symbols ('|', '&', '!', and '%') with colon (':') is not allowed. Please only use one set of symbols. $explanation"
            else s"Mixing the IS keyword with colon (':') between labels is not allowed. $explanation",
            pos
          )
        case None => SemanticCheck.success
      }
    }(state)
  }

  private def sortLabelExpressionIntoPartition(
    labelExpression: LabelExpression,
    isNode: Boolean,
    partition: LabelExpressionsPartition
  ): LabelExpressionsPartition = {
    labelExpression match {
      case x: Leaf => partition.copy(leaf = partition.leaf + x)
      case Disjunctions(children, _) if !isNode && children.forall(_.isInstanceOf[Leaf]) => partition
      case x if isNode && x.containsGpmSpecificLabelExpression    => partition.copy(gpm = partition.gpm + x)
      case x if !isNode && x.containsGpmSpecificRelTypeExpression => partition.copy(gpm = partition.gpm + x)
      case x                                                      => partition.copy(legacy = partition.legacy + x)
    }
  }

  private def checkIfMixingLegacyVarLengthWithQPPs: SemanticCheck = {
    val legacyVarLengthRelationships = this.folder.treeFold(Seq.empty[RelationshipPattern]) {
      case r @ RelationshipPattern(_, _, Some(_), _, _, _) => acc => TraverseChildren(acc :+ r)
      case _: SubqueryCall | _: FullSubqueryExpression     => acc => SkipChildren(acc)
    }
    val hasQPP = this.folder.treeFold(false) {
      case _: QuantifiedPath                           => _ => SkipChildren(true)
      case _: SubqueryCall | _: FullSubqueryExpression => acc => SkipChildren(acc)
      case _                                           => acc => if (acc) SkipChildren(acc) else TraverseChildren(acc)
    }

    when(hasQPP) {
      legacyVarLengthRelationships.foldSemanticCheck { legacyVarLengthRelationship =>
        error(
          "Mixing variable-length relationships ('-[*]-') with quantified relationships ('()-->*()') or quantified path patterns ('(()-->())*') is not allowed.",
          legacyVarLengthRelationship.position
        )
      }
    }
  }

  def clauseSpecificSemanticCheck: SemanticCheck
}

sealed trait UpdateClause extends Clause {
  override def returnVariables: ReturnVariables = ReturnVariables.empty
}

case class LoadCSV(
  withHeaders: Boolean,
  urlString: Expression,
  variable: Variable,
  fieldTerminator: Option[StringLiteral]
)(val position: InputPosition) extends Clause with SemanticAnalysisTooling {
  override def name: String = "LOAD CSV"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    SemanticExpressionCheck.simple(urlString) chain
      expectType(CTString.covariant, urlString) chain
      checkFieldTerminator chain
      typeCheck

  private def checkFieldTerminator: SemanticCheck = {
    fieldTerminator match {
      case Some(literal) if literal.value.length != 1 =>
        error("CSV field terminator can only be one character wide", literal.position)
      case _ => success
    }
  }

  private def typeCheck: SemanticCheck = {
    val typ =
      if (withHeaders)
        CTMap
      else
        CTList(CTString)

    declareVariable(variable, typ)
  }
}

case class InputDataStream(variables: Seq[Variable])(val position: InputPosition) extends Clause
    with SemanticAnalysisTooling {

  override def name: String = "INPUT DATA STREAM"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    variables.foldSemanticCheck(v => declareVariable(v, types(v)))
}

sealed trait GraphSelection extends Clause with SemanticAnalysisTooling {
  def expression: Expression
}

final case class UseGraph(expression: Expression)(val position: InputPosition) extends GraphSelection {
  override def name = "USE GRAPH"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    whenState(_.features(SemanticFeature.UseAsMultipleGraphsSelector))(
      thenBranch = checkDynamicGraphSelector,
      elseBranch = whenState(_.features(SemanticFeature.UseAsSingleGraphSelector))(
        // On clause level, this feature means that only static graph references are allowed
        thenBranch = checkStaticGraphSelector,
        elseBranch = unsupported()
      )
    )

  private def unsupported(): SemanticCheck = SemanticCheck.fromFunctionWithContext { (semanticState, context) =>
    SemanticCheckResult.error(semanticState, context.errorMessageProvider.createUseClauseUnsupportedError(), position)
  }

  private def checkDynamicGraphSelector: SemanticCheck =
    checkGraphReference chain checkDynamicGraphReference

  private def checkDynamicGraphReference: SemanticCheck =
    graphReference.foldSemanticCheck {
      case ViewRef(_, arguments)        => checkExpressions(arguments)
      case GraphRefParameter(parameter) => checkExpressions(Seq(parameter))
      case _                            => success
    }

  private def checkExpressions(expressions: Seq[Expression]): SemanticCheck =
    expressions.foldSemanticCheck(expr =>
      SemanticExpressionCheck.check(Expression.SemanticContext.Results, expr)
    )

  private def checkStaticGraphSelector: SemanticCheck =
    checkGraphReference chain checkStaticGraphReference

  private def checkGraphReference: SemanticCheck =
    GraphReference.checkNotEmpty(graphReference, expression.position)

  private def checkStaticGraphReference: SemanticCheck = {

    def dynamicGraphReferenceError(): SemanticCheck =
      SemanticCheck.fromFunctionWithContext { (semanticState, context) =>
        SemanticCheckResult.error(
          semanticState,
          context.errorMessageProvider.createDynamicGraphReferenceUnsupportedError(),
          position
        )
      }

    graphReference.foldSemanticCheck {
      case _: GraphRef => success
      case _           => dynamicGraphReferenceError()
    }
  }

  private def graphReference: Option[GraphReference] =
    GraphReference.from(expression)

}

object GraphReference extends SemanticAnalysisTooling {

  def from(expression: Expression): Option[GraphReference] = {

    def fqn(expr: Expression): Option[List[String]] = expr match {
      case p: Property           => fqn(p.map).map(_ :+ p.propertyKey.name)
      case v: Variable           => Some(List(v.name))
      case f: FunctionInvocation => Some(f.namespace.parts :+ f.functionName.name)
      case _                     => None
    }

    (expression, fqn(expression)) match {
      case (f: FunctionInvocation, Some(name)) => Some(ViewRef(CatalogName(name), f.args)(f.position))
      case (p: Parameter, _)                   => Some(GraphRefParameter(p)(p.position))
      case (e, Some(name))                     => Some(GraphRef(CatalogName(name))(e.position))
      case _                                   => None
    }
  }

  def checkNotEmpty(gr: Option[GraphReference], pos: InputPosition): SemanticCheck =
    when(gr.isEmpty)(error("Invalid graph reference", pos))
}

sealed trait GraphReference extends ASTNode with SemanticCheckable {
  override def semanticCheck: SemanticCheck = success
}

final case class GraphRef(name: CatalogName)(val position: InputPosition) extends GraphReference

final case class ViewRef(name: CatalogName, arguments: Seq[Expression])(val position: InputPosition)
    extends GraphReference with SemanticAnalysisTooling {

  override def semanticCheck: SemanticCheck =
    arguments.zip(argumentsAsGraphReferences).foldSemanticCheck { case (arg, gr) =>
      GraphReference.checkNotEmpty(gr, arg.position)
    }

  def argumentsAsGraphReferences: Seq[Option[GraphReference]] =
    arguments.map(GraphReference.from)
}

final case class GraphRefParameter(parameter: Parameter)(val position: InputPosition) extends GraphReference

trait SingleRelTypeCheck {
  self: Clause =>

  protected def checkRelTypes(patternPart: PatternPart): SemanticCheck =
    patternPart match {
      case PathPatternPart(element) => checkRelTypes(element)
      case _                        => success
    }

  protected def checkRelTypes(pattern: Pattern): SemanticCheck =
    pattern.patternParts.foldSemanticCheck(checkRelTypes)

  private def checkRelTypes(patternElement: PatternElement): SemanticCheck = {
    patternElement match {
      case RelationshipChain(element, rel, _) =>
        checkRelTypes(rel) chain checkRelTypes(element)
      case _ => success
    }
  }

  protected def checkRelTypes(rel: RelationshipPattern): SemanticCheck =
    rel.labelExpression match {
      case None => SemanticError(
          s"Exactly one relationship type must be specified for ${self.name}. Did you forget to prefix your relationship type with a ':'?",
          rel.position
        )
      case Some(Leaf(RelTypeName(_), _)) => success
      case Some(other) =>
        val types = other.flatten.distinct
        val (maybePlain, exampleString) =
          if (types.size == 1) ("plain ", s"like `:${types.head.name}` ")
          else ("", "")
        SemanticError(
          s"A single ${maybePlain}relationship type ${exampleString}must be specified for ${self.name}",
          rel.position
        )
    }
}

object Match {
  protected val hintPrettifier: Prettifier = Prettifier(ExpressionStringifier())
}

case class Match(
  optional: Boolean,
  matchMode: MatchMode,
  pattern: Pattern.ForMatch,
  hints: Seq[UsingHint],
  where: Option[Where]
)(val position: InputPosition) extends Clause with SemanticAnalysisTooling {
  override def name = "MATCH"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    noImplicitJoinsInQuantifiedPathPatterns chain
      SemanticPatternCheck.check(Pattern.SemanticContext.Match, pattern) chain
      hints.semanticCheck chain
      uniqueHints chain
      checkMatchMode chain
      where.semanticCheck chain
      checkHints chain
      checkForCartesianProducts

  /**
   * Ensure that the node and relationship variables defined inside the quantified path patterns contained in this MATCH clause do not form any implicit joins.
   * It must run before checking the pattern itself – as it relies on variables defined in previous clauses to pre-empt some of the errors.
   * It checks for three scenarios:
   *   - a variable is defined in two or more quantified path patterns inside this MATCH clause
   *   - a variable is defined in a quantified path pattern and in a non-quantified node or relationship pattern inside this MATCH clause
   *   - a variable is defined in a quantified path pattern inside this MATCH clause and also appears in a previous MATCH clause
   */
  private def noImplicitJoinsInQuantifiedPathPatterns: SemanticCheck =
    SemanticCheck.fromState { state =>
      val (quantifiedPaths, simplePatterns) = partitionPatternElements(pattern.patternParts.map(_.element).toList)

      val allVariablesInQuantifiedPaths: List[(LogicalVariable, QuantifiedPath)] =
        for {
          quantifiedPath <- quantifiedPaths
          variable <- quantifiedPath.allVariables
        } yield variable -> quantifiedPath

      val quantifiedPathsPerVariable: Map[LogicalVariable, List[QuantifiedPath]] =
        allVariablesInQuantifiedPaths.groupMap(_._1)(_._2)

      val allVariablesInSimplePatterns: Set[LogicalVariable] =
        simplePatterns.flatMap(_.allVariables).toSet

      val semanticErrors =
        quantifiedPathsPerVariable.flatMap { case (variable, paths) =>
          List(
            Option.when(paths.size > 1) {
              s"The variable `${variable.name}` occurs in multiple quantified path patterns and needs to be renamed."
            },
            Option.when(allVariablesInSimplePatterns.contains(variable)) {
              s"The variable `${variable.name}` occurs both inside and outside a quantified path pattern and needs to be renamed."
            },
            Option.when(state.symbol(variable.name).isDefined) {
              // Because one cannot refer to a variable defined in a subsequent clause, if the variable exists in the semantic state, then it must have been defined in a previous clause.
              s"The variable `${variable.name}` is already defined in a previous clause, it cannot be referenced as a node or as a relationship variable inside of a quantified path pattern."
            }
          ).flatten.map { errorMessage =>
            SemanticError(errorMessage, variable.position)
          }
        }

      SemanticCheck.error(semanticErrors)
    }

  /**
   * Recursively partition sub-elements into quantified paths and "simple" patterns (nodes and relationships).
   * @param patternElements the list of elements to break down and partition
   * @param quantifiedPaths accumulator for quantified paths
   * @param simplePatterns accumulator for simple patterns
   * @return the list of quantified paths and the list of simple patterns
   */
  @tailrec
  private def partitionPatternElements(
    patternElements: List[PatternElement],
    quantifiedPaths: List[QuantifiedPath] = Nil,
    simplePatterns: List[SimplePattern] = Nil
  ): (List[QuantifiedPath], List[SimplePattern]) =
    patternElements match {
      case Nil => (quantifiedPaths.reverse, simplePatterns.reverse)
      case element :: otherElements =>
        element match {
          case PathConcatenation(factors) =>
            partitionPatternElements(factors.toList ++ otherElements, quantifiedPaths, simplePatterns)
          case ParenthesizedPath(part, _) =>
            partitionPatternElements(part.element :: otherElements, quantifiedPaths, simplePatterns)
          case quantifiedPath: QuantifiedPath =>
            partitionPatternElements(otherElements, quantifiedPath :: quantifiedPaths, simplePatterns)
          case simplePattern: SimplePattern =>
            partitionPatternElements(otherElements, quantifiedPaths, simplePattern :: simplePatterns)
        }
    }

  private def uniqueHints: SemanticCheck = {
    val errors = hints.collect {
      case h: UsingJoinHint => h.variables.toIndexedSeq
    }.flatten
      .groupBy(identity)
      .collect {
        case (variable, identHints) if identHints.size > 1 =>
          SemanticError("Multiple join hints for same variable are not supported", variable.position)
      }.toVector

    state: SemanticState => semantics.SemanticCheckResult(state, errors)
  }

  private def checkForCartesianProducts: SemanticCheck = (state: SemanticState) => {
    val cc = connectedComponents(pattern.patternParts)
    // if we have multiple connected components we will have
    // a cartesian product
    val newState = cc.drop(1).foldLeft(state) { (innerState, component) =>
      innerState.addNotification(CartesianProductNotification(position, component.variables.map(_.name)))
    }

    semantics.SemanticCheckResult(newState, Seq.empty)
  }

  private def checkMatchMode: SemanticCheck = {
    whenState(!_.features.contains(SemanticFeature.MatchModes)) {
      matchMode match {
        case mode: DifferentRelationships if mode.implicitlyCreated => SemanticCheckResult.success(_)
        case _ => error(s"Match modes such as `${matchMode.prettified}` are not supported yet.", matchMode.position)
      }
    } ifOkChain {
      matchMode match {
        case _: RepeatableElements     => checkRepeatableElements(_)
        case _: DifferentRelationships => checkDifferentRelationships(_)
      }
    }
  }

  private def checkRepeatableElements(state: SemanticState): SemanticCheckResult = {
    val errors = pattern.patternParts.collect {
      case part if !part.isBounded =>
        SemanticError(
          "The pattern may yield an infinite number of rows under match mode REPEATABLE ELEMENTS, " +
            "perhaps use a path selector or add an upper bound to your quantified path patterns.",
          part.position
        )
    }
    semantics.SemanticCheckResult(state, errors)
  }

  /**
   * Iff we are operating under a DIFFERENT RELATIONSHIPS match mode, then a selective selector
   * (any other selector than ALL) would imply an order of evaluation of the different path patterns.
   * Therefore, once there is at least one path pattern with a selective selector, then we need to make sure
   * that there is no other path pattern beside it.
   */
  private def checkDifferentRelationships(state: SemanticState): SemanticCheckResult = {
    // Let's only mention match modes when that is an available feature
    def errorMessage: String = if (state.features.contains(SemanticFeature.MatchModes)) {
      "Multiple path patterns cannot be used in the same clause in combination with a selective path selector. " +
        "You may want to use multiple MATCH clauses, or you might want to consider using the REPEATABLE ELEMENTS match mode."
    } else {
      "Multiple path patterns cannot be used in the same clause in combination with a selective path selector."
    }

    val errors = if (pattern.patternParts.size > 1) {
      pattern.patternParts
        .find(_.isSelective)
        .map(selectivePattern =>
          SemanticError(
            errorMessage,
            selectivePattern.position
          )
        )
        .toSeq
    } else {
      Seq.empty
    }
    semantics.SemanticCheckResult(state, errors)
  }

  private def checkHints: SemanticCheck = SemanticCheck.fromFunctionWithContext { (semanticState, context) =>
    def getMissingEntityKindError(variable: String, labelOrRelTypeName: String, hint: Hint): String = {
      val isNode = semanticState.isNode(variable)
      val typeName = if (isNode) "label" else "relationship type"
      val functionName = if (isNode) "labels" else "type"
      val operatorDescription = hint match {
        case _: UsingIndexHint => "index"
        case _: UsingScanHint  => s"$typeName scan"
        case _: UsingJoinHint  => "join"
      }
      val typePredicates = getLabelAndRelTypePredicates(variable).distinct
      val foundTypePredicatesDescription = typePredicates match {
        case Seq()              => s"no $typeName was"
        case Seq(typePredicate) => s"only the $typeName `$typePredicate` was"
        case typePredicates     => s"only the ${typeName}s `${typePredicates.mkString("`, `")}` were"
      }

      getHintErrorForVariable(
        operatorDescription,
        hint,
        s"$typeName `$labelOrRelTypeName`",
        foundTypePredicatesDescription,
        variable,
        s"""Predicates must include the $typeName literal `$labelOrRelTypeName`.
            | That is, the function `$functionName()` is not compatible with indexes.""".stripLinesAndMargins
      )
    }

    def getMissingPropertyError(hint: UsingIndexHint): String = {
      val variable = hint.variable.name
      val propertiesInHint = hint.properties
      val plural = propertiesInHint.size > 1
      val foundPropertiesDescription = getPropertyPredicates(variable) match {
        case Seq()         => "none was"
        case Seq(property) => s"only `$property` was"
        case properties    => s"only `${properties.mkString("`, `")}` were"
      }
      val missingPropertiesNames = propertiesInHint.map(prop => s"`${prop.name}`").mkString(", ")
      val missingPropertiesDescription = s"the ${if (plural) "properties" else "property"} $missingPropertiesNames"

      getHintErrorForVariable(
        "index",
        hint,
        missingPropertiesDescription,
        foundPropertiesDescription,
        variable,
        """Supported predicates are:
          | equality comparison, inequality (range) comparison, `STARTS WITH`,
          | `IN` condition or checking property existence.
          | The comparison cannot be performed between two property values.""".stripLinesAndMargins
      )
    }

    def getHintErrorForVariable(
      operatorDescription: String,
      hint: Hint,
      missingThingDescription: String,
      foundThingsDescription: String,
      variable: String,
      additionalInfo: String
    ): String = {
      val isNode = semanticState.isNode(variable)
      val entityName = if (isNode) "node" else "relationship"

      getHintError(
        operatorDescription,
        hint,
        missingThingDescription,
        foundThingsDescription,
        s"the $entityName `$variable`",
        entityName,
        additionalInfo
      )
    }

    def getHintError(
      operatorDescription: String,
      hint: Hint,
      missingThingDescription: String,
      foundThingsDescription: String,
      entityDescription: String,
      entityName: String,
      additionalInfo: String
    ): String = {
      context.errorMessageProvider.createMissingPropertyLabelHintError(
        operatorDescription,
        hintPrettifier.asString(hint),
        missingThingDescription,
        foundThingsDescription,
        entityDescription,
        entityName,
        additionalInfo
      )
    }

    val error: Option[SemanticErrorDef] = hints.collectFirst {
      case hint @ UsingIndexHint(Variable(variable), LabelOrRelTypeName(labelOrRelTypeName), _, _, _)
        if !containsLabelOrRelTypePredicate(variable, labelOrRelTypeName) =>
        SemanticError(getMissingEntityKindError(variable, labelOrRelTypeName, hint), hint.position)
      case hint @ UsingIndexHint(Variable(variable), LabelOrRelTypeName(_), properties, _, _)
        if !containsPropertyPredicates(variable, properties) =>
        SemanticError(getMissingPropertyError(hint), hint.position)
      case hint @ UsingScanHint(Variable(variable), LabelOrRelTypeName(labelOrRelTypeName))
        if !containsLabelOrRelTypePredicate(variable, labelOrRelTypeName) =>
        SemanticError(getMissingEntityKindError(variable, labelOrRelTypeName, hint), hint.position)
      case hint @ UsingJoinHint(_) if pattern.length == 0 =>
        SemanticError("Cannot use join hint for single node pattern.", hint.position)
    }
    SemanticCheckResult(semanticState, error.toSeq)
  }

  private[ast] def containsPropertyPredicates(variable: String, propertiesInHint: Seq[PropertyKeyName]): Boolean = {
    val propertiesInPredicates: Seq[String] = getPropertyPredicates(variable)

    propertiesInHint.forall(p => propertiesInPredicates.contains(p.name))
  }

  private def getPropertyPredicates(variable: String): Seq[String] = {
    where.map(w => collectPropertiesInPredicates(variable, w.expression)).getOrElse(Seq.empty[String]) ++
      pattern.folder.treeFold(Seq.empty[String]) {
        case NodePattern(Some(Variable(id)), _, properties, predicate) if variable == id =>
          acc =>
            SkipChildren(acc ++ collectPropertiesInPropertyMap(properties) ++ predicate.map(
              collectPropertiesInPredicates(variable, _)
            ).getOrElse(Seq.empty[String]))
        case RelationshipPattern(Some(Variable(id)), _, _, properties, predicate, _) if variable == id =>
          acc =>
            SkipChildren(acc ++ collectPropertiesInPropertyMap(properties) ++ predicate.map(
              collectPropertiesInPredicates(variable, _)
            ).getOrElse(Seq.empty[String]))
      }
  }

  private def collectPropertiesInPropertyMap(properties: Option[Expression]): Seq[String] =
    properties match {
      case Some(MapExpression(prop)) => prop.map(_._1.name)
      case _                         => Seq.empty[String]
    }

  private def collectPropertiesInPredicates(variable: String, whereExpression: Expression): Seq[String] =
    whereExpression.folder.treeFold(Seq.empty[String]) {
      case Equals(Property(Variable(id), PropertyKeyName(name)), other) if id == variable && applicable(other) =>
        acc => SkipChildren(acc :+ name)
      case Equals(other, Property(Variable(id), PropertyKeyName(name))) if id == variable && applicable(other) =>
        acc => SkipChildren(acc :+ name)
      case In(Property(Variable(id), PropertyKeyName(name)), _) if id == variable =>
        acc => SkipChildren(acc :+ name)
      case IsNotNull(Property(Variable(id), PropertyKeyName(name))) if id == variable =>
        acc => SkipChildren(acc :+ name)
      case StartsWith(Property(Variable(id), PropertyKeyName(name)), _) if id == variable =>
        acc => SkipChildren(acc :+ name)
      case EndsWith(Property(Variable(id), PropertyKeyName(name)), _) if id == variable =>
        acc => SkipChildren(acc :+ name)
      case Contains(Property(Variable(id), PropertyKeyName(name)), _) if id == variable =>
        acc => SkipChildren(acc :+ name)
      case FunctionInvocation(
          Namespace(List(namespace)),
          FunctionName(functionName),
          _,
          Seq(Property(Variable(id), PropertyKeyName(name)), _, _)
        ) if id == variable && namespace.equalsIgnoreCase("point") && functionName.equalsIgnoreCase("withinBBox") =>
        acc => SkipChildren(acc :+ name)
      case expr: InequalityExpression =>
        acc =>
          val newAcc: Seq[String] = Seq(expr.lhs, expr.rhs).foldLeft(acc) { (acc, expr) =>
            expr match {
              case Property(Variable(id), PropertyKeyName(name)) if id == variable =>
                acc :+ name
              case FunctionInvocation(
                  Namespace(List(namespace)),
                  FunctionName(functionName),
                  _,
                  Seq(Property(Variable(id), PropertyKeyName(name)), _)
                )
                if id == variable && namespace.equalsIgnoreCase("point") && functionName.equalsIgnoreCase("distance") =>
                acc :+ name
              case _ =>
                acc
            }
          }
          SkipChildren(newAcc)
      case _: Where | _: And | _: Ands | _: Set[_] | _: Seq[_] | _: Or | _: Ors | _: Not =>
        acc => TraverseChildren(acc)
      case _ =>
        acc => SkipChildren(acc)
    }

  /**
   * Checks validity of the other side, X, of expressions such as
   * `USING INDEX ON n:Label(prop) WHERE n.prop = X (or X = n.prop)`
   *
   * Returns true if X is a valid expression in this context, otherwise false.
   */
  private def applicable(other: Expression): Boolean = {
    other match {
      case f: FunctionInvocation => !isIdFunction(f)
      case _                     => true
    }
  }

  private[ast] def containsLabelOrRelTypePredicate(variable: String, labelOrRelType: String): Boolean =
    getLabelAndRelTypePredicates(variable).contains(labelOrRelType)

  private def getLabelsFromLabelExpression(labelExpression: LabelExpression) = {
    labelExpression.flatten.map(_.name)
  }

  private def getLabelAndRelTypePredicates(variable: String): Seq[String] = {
    val inlinedRelTypes = pattern.folder.fold(Seq.empty[String]) {
      case RelationshipPattern(Some(Variable(id)), Some(labelExpression), _, _, _, _) if variable == id =>
        list => list ++ getLabelsFromLabelExpression(labelExpression)
    }

    val labelExpressionLabels: Seq[String] = pattern.folder.fold(Seq.empty[String]) {
      case NodePattern(Some(Variable(id)), Some(labelExpression), _, _) if variable == id =>
        list => list ++ getLabelsFromLabelExpression(labelExpression)
    }

    val (predicateLabels, predicateRelTypes) = where match {
      case Some(innerWhere) => innerWhere.folder.treeFold((Seq.empty[String], Seq.empty[String])) {
          // These are predicates from the match pattern that were rewritten
          case HasLabels(Variable(id), predicateLabels) if id == variable => {
            case (ls, rs) => SkipChildren((ls ++ predicateLabels.map(_.name), rs))
          }
          case HasTypes(Variable(id), predicateRelTypes) if id == variable => {
            case (ls, rs) => SkipChildren((ls, rs ++ predicateRelTypes.map(_.name)))
          }
          // These are predicates in the where clause that have not been rewritten yet.
          case LabelExpressionPredicate(Variable(id), labelExpression) if id == variable => {
            case (ls, rs) =>
              val labelOrRelTypes = getLabelsFromLabelExpression(labelExpression)
              SkipChildren((ls ++ labelOrRelTypes, rs ++ labelOrRelTypes))
          }
          case _: Where | _: And | _: Ands | _: Set[_] | _: Seq[_] | _: Or | _: Ors =>
            acc => TraverseChildren(acc)
          case _ =>
            acc => SkipChildren(acc)
        }
      case None => (Seq.empty, Seq.empty)
    }

    val allLabels = labelExpressionLabels ++ predicateLabels
    val allRelTypes = inlinedRelTypes ++ predicateRelTypes
    allLabels ++ allRelTypes
  }

  def allExportedVariables: Set[LogicalVariable] = pattern.patternParts.folder.findAllByClass[LogicalVariable].toSet
}

case class Merge(pattern: NonPrefixedPatternPart, actions: Seq[MergeAction], where: Option[Where] = None)(
  val position: InputPosition
) extends UpdateClause with SingleRelTypeCheck {

  override def name = "MERGE"

  override protected def shouldRunGpmChecks: Boolean = false

  private def checkNoSubqueryInMerge: SemanticCheck = {
    val hasSubqueryExpression = Seq(pattern, actions).folder.treeCollect {
      case e: FullSubqueryExpression => e
    }

    hasSubqueryExpression match {
      case subquery +: _ =>
        SemanticCheck.error(SemanticError("Subquery expressions are not allowed in a MERGE clause.", subquery.position))
      case _ => success
    }
  }

  override def clauseSpecificSemanticCheck: SemanticCheck =
    SemanticPatternCheck.check(Pattern.SemanticContext.Merge, Pattern.ForUpdate(Seq(pattern))(pattern.position)) chain
      actions.semanticCheck chain
      checkRelTypes(pattern) chain
      where.semanticCheck chain
      checkNoSubqueryInMerge
}

case class Create(pattern: Pattern.ForUpdate)(val position: InputPosition) extends UpdateClause
    with SingleRelTypeCheck {
  override def name = "CREATE"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    SemanticPatternCheck.check(Pattern.SemanticContext.Create, pattern) chain
      checkRelTypes(pattern) chain
      SemanticState.recordCurrentScope(pattern)

  override protected def shouldRunGpmChecks: Boolean = false
}

case class CreateUnique(pattern: Pattern.ForUpdate)(val position: InputPosition) extends UpdateClause {
  override def name = "CREATE UNIQUE"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    SemanticError("CREATE UNIQUE is no longer supported. Please use MERGE instead", position)

}

case class SetClause(items: Seq[SetItem])(val position: InputPosition) extends UpdateClause {
  override def name = "SET"

  override def clauseSpecificSemanticCheck: SemanticCheck = items.semanticCheck
}

case class Delete(expressions: Seq[Expression], forced: Boolean)(val position: InputPosition) extends UpdateClause {
  override def name = "DELETE"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    SemanticExpressionCheck.simple(expressions) chain
      warnAboutDeletingLabels chain
      expectType(CTNode.covariant | CTRelationship.covariant | CTPath.covariant, expressions)

  private def warnAboutDeletingLabels =
    expressions.filter(e => e.isInstanceOf[LabelExpressionPredicate]) map {
      e => SemanticError("DELETE doesn't support removing labels from a node. Try REMOVE.", e.position)
    }
}

case class Remove(items: Seq[RemoveItem])(val position: InputPosition) extends UpdateClause {
  override def name = "REMOVE"

  override def clauseSpecificSemanticCheck: SemanticCheck = items.semanticCheck
}

case class Foreach(
  variable: Variable,
  expression: Expression,
  updates: Seq[Clause]
)(val position: InputPosition) extends UpdateClause {
  override def name = "FOREACH"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    SemanticExpressionCheck.simple(expression) chain
      expectType(CTList(CTAny).covariant, expression) chain
      updates.filter(!_.isInstanceOf[UpdateClause]).map(c =>
        SemanticError(s"Invalid use of ${c.name} inside FOREACH", c.position)
      ) ifOkChain
      withScopedState {
        val possibleInnerTypes: TypeGenerator = types(expression)(_).unwrapLists
        declareVariable(variable, possibleInnerTypes) chain updates.semanticCheck
      }
}

case class Unwind(
  expression: Expression,
  variable: Variable
)(val position: InputPosition) extends Clause with SemanticAnalysisTooling {
  override def name = "UNWIND"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    SemanticExpressionCheck.check(SemanticContext.Results, expression) chain
      expectType(CTList(CTAny).covariant | CTAny.covariant, expression) ifOkChain
      FilteringExpressions.failIfAggregating(expression) chain {
        val possibleInnerTypes: TypeGenerator = types(expression)(_).unwrapPotentialLists
        declareVariable(variable, possibleInnerTypes)
      }
}

abstract class CallClause extends Clause {
  override def name = "CALL"

  def containsNoUpdates: Boolean

  def yieldAll: Boolean
}

case class UnresolvedCall(
  procedureNamespace: Namespace,
  procedureName: ProcedureName,
  // None: No arguments given
  declaredArguments: Option[Seq[Expression]] = None,
  // None: No results declared  (i.e. no "YIELD" part or "YIELD *")
  declaredResult: Option[ProcedureResult] = None,
  // YIELD *
  override val yieldAll: Boolean = false
)(val position: InputPosition) extends CallClause {

  override def returnVariables: ReturnVariables =
    ReturnVariables(
      includeExisting = false,
      declaredResult.map(_.items.map(_.variable).toList).getOrElse(List.empty)
    )

  override def clauseSpecificSemanticCheck: SemanticCheck = {
    val argumentCheck = declaredArguments.map(
      SemanticExpressionCheck.check(SemanticContext.Results, _)
    ).getOrElse(success)
    val resultsCheck = declaredResult.map(_.semanticCheck).getOrElse(success)
    val invalidExpressionsCheck = declaredArguments.map(_.map {
      case arg if arg.containsAggregate =>
        SemanticCheck.error(
          SemanticError(
            """Procedure call cannot take an aggregating function as argument, please add a 'WITH' to your statement.
              |For example:
              |    MATCH (n:Person) WITH collect(n.name) AS names CALL proc(names) YIELD value RETURN value""".stripMargin,
            position
          )
        )
      case _ => success
    }.foldLeft(success)(_ chain _)).getOrElse(success)

    argumentCheck chain resultsCheck chain invalidExpressionsCheck
  }

  // At this stage we can't know this, so we assume the CALL is non updating,
  // it should be rechecked when the call is resolved
  override def containsNoUpdates = true
}

sealed trait HorizonClause extends Clause with SemanticAnalysisTooling {
  override def clauseSpecificSemanticCheck: SemanticCheck = SemanticState.recordCurrentScope(this)

  def semanticCheckContinuation(previousScope: Scope, outerScope: Option[Scope] = None): SemanticCheck
}

object ProjectionClause {

  def unapply(arg: ProjectionClause)
    : Option[(Boolean, ReturnItems, Option[OrderBy], Option[Skip], Option[Limit], Option[Where])] = {
    arg match {
      case With(distinct, ri, orderBy, skip, limit, where, _) => Some((distinct, ri, orderBy, skip, limit, where))
      case Return(distinct, ri, orderBy, skip, limit, _, _)   => Some((distinct, ri, orderBy, skip, limit, None))
      case Yield(ri, orderBy, skip, limit, where)             => Some((false, ri, orderBy, skip, limit, where))
    }
  }

  def checkAliasedReturnItems(returnItems: ReturnItems, clauseName: String): SemanticState => Seq[SemanticError] =
    state =>
      returnItems match {
        case li: ReturnItems =>
          li.items.filter(item => item.alias.isEmpty).map(i =>
            SemanticError(s"Expression in $clauseName must be aliased (use AS)", i.position)
          )
        case _ => Seq()
      }
}

sealed trait ProjectionClause extends HorizonClause {
  def distinct: Boolean

  def returnItems: ReturnItems

  def orderBy: Option[OrderBy]

  def where: Option[Where]

  def skip: Option[Skip]

  def limit: Option[Limit]

  final def isWith: Boolean = !isReturn

  def isReturn: Boolean = false

  def name: String

  def copyProjection(
    distinct: Boolean = this.distinct,
    returnItems: ReturnItems = this.returnItems,
    orderBy: Option[OrderBy] = this.orderBy,
    skip: Option[Skip] = this.skip,
    limit: Option[Limit] = this.limit,
    where: Option[Where] = this.where
  ): ProjectionClause = {
    this match {
      case w: With   => w.copy(distinct, returnItems, orderBy, skip, limit, where)(this.position)
      case r: Return => r.copy(distinct, returnItems, orderBy, skip, limit, r.excludedNames)(this.position)
      case y: Yield  => y.copy(returnItems, orderBy, skip, limit, where)(this.position)
    }
  }

  /**
   * @return copy of this ProjectionClause with new return items
   */
  def withReturnItems(items: Seq[ReturnItem]): ProjectionClause

  override def clauseSpecificSemanticCheck: SemanticCheck =
    returnItems.semanticCheck

  override def semanticCheckContinuation(previousScope: Scope, outerScope: Option[Scope] = None): SemanticCheck =
    SemanticCheck.fromState {
      state: SemanticState =>
        /**
       * scopeToImportVariablesFrom will provide the scope to bring over only the variables that are needed from the
       * previous scope
       */
        def runChecks(scopeToImportVariablesFrom: Scope): SemanticCheck = {
          returnItems.declareVariables(scopeToImportVariablesFrom) chain
            orderBy.semanticCheck chain
            checkSkip chain
            checkLimit chain
            where.semanticCheck
        }

        // The two clauses ORDER BY and WHERE, following a WITH clause where there is no DISTINCT nor aggregation, have a special scope such that they
        // can see both variables from before the WITH and variables introduced by the WITH
        // (SKIP and LIMIT clauses are not allowed to access variables anyway, so they do not need to be included in this condition even when they are standalone)
        val specialScopeForSubClausesNeeded = orderBy.isDefined || where.isDefined
        val canSeePreviousScope =
          (!(returnItems.containsAggregate || distinct || isInstanceOf[Yield])) || returnItems.includeExisting

        val check: SemanticCheck =
          if (specialScopeForSubClausesNeeded && canSeePreviousScope) {
            /*
             * We have `WITH ... WHERE` or `WITH ... ORDER BY` with no aggregation nor distinct meaning we can
             *  see things from previous scopes when we are done here
             *  (incoming-scope)
             *        |      \
             *        |     (child scope) <-  semantic checking of `ORDER BY` and `WHERE` discarded, only used for errors
             *        |
             *  (outgoing-scope)
             *        |
             *       ...
             */

            for {
              // Special scope for ORDER BY and WHERE (SKIP and LIMIT are also checked in isolated scopes)
              _ <- SemanticCheck.setState(state.newChildScope)
              checksResult <- runChecks(previousScope)
              // New sibling scope for the WITH/RETURN clause itself and onwards.
              // Re-declare projected variables in the new scope since the sub-scope is discarded
              // (We do not need to check warnOnAccessToRestrictedVariableInOrderByOrWhere here since that only applies when we have distinct or aggregation)
              returnState <- SemanticCheck.setState(checksResult.state.popScope.newSiblingScope)
              finalResult <- returnItems.declareVariables(state.currentScope.scope)
            } yield {
              SemanticCheckResult(finalResult.state, checksResult.errors ++ finalResult.errors)
            }
          } else if (specialScopeForSubClausesNeeded) {
            /*
             *  We have `WITH ... WHERE` or `WITH ... ORDER BY` with an aggregation or a distinct meaning we cannot
             *  see things from previous scopes after the aggregation (or distinct).
             *
             *  (incoming-scope)
             *         |
             *  (outgoing-scope)
             *         |      \
             *         |      (child-scope) <- semantic checking of `ORDER BY` and `WHERE` discarded only used for errors
             *        ...
             */

            // Introduce a new sibling scope first, and then a new child scope from that one
            // this child scope is used for errors only and will later be discarded.
            val siblingState = state.newSiblingScope
            val stateForSubClauses = siblingState.newChildScope

            for {
              _ <- SemanticCheck.setState(stateForSubClauses)
              checksResult <- runChecks(previousScope)
              // By popping the scope we will discard the special scope used for subclauses
              returnResult <- SemanticCheck.setState(checksResult.state.popScope)
              // Re-declare projected variables in the new scope since the sub-scope is discarded
              finalResult <-
                returnItems.declareVariables(previousScope)
            } yield {
              // Re-declare projected variables in the new scope since the sub-scope is discarded
              val niceErrors = (checksResult.errors ++ finalResult.errors).map(
                warnOnAccessToRestrictedVariableInOrderByOrWhere(state.currentScope.symbolNames)
              )
              SemanticCheckResult(finalResult.state, niceErrors)
            }
          } else {
            for {
              _ <- SemanticCheck.setState(state.newSiblingScope)
              checksResult <- runChecks(previousScope)
            } yield {
              val niceErrors = checksResult.errors.map(
                warnOnAccessToRestrictedVariableInOrderByOrWhere(state.currentScope.symbolNames)
              )
              SemanticCheckResult(checksResult.state, niceErrors)

            }
          }

        (isReturn, outerScope) match {
          case (true, Some(outer)) => check.map { result =>
              val outerScopeSymbolNames = outer.symbolNames
              val outputSymbolNames = result.state.currentScope.scope.symbolNames
              val alreadyDeclaredNames = outputSymbolNames.intersect(outerScopeSymbolNames)
              val explicitReturnVariablesByName =
                returnItems.returnVariables.explicitVariables.map(v => v.name -> v).toMap
              val errors = alreadyDeclaredNames.map { name =>
                val position = explicitReturnVariablesByName.getOrElse(name, returnItems).position
                SemanticError(s"Variable `$name` already declared in outer scope", position)
              }

              SemanticCheckResult(result.state, result.errors ++ errors)
            }

          case _ =>
            check
        }
    }

  /**
   * If you access a previously defined variable in a WITH/RETURN with DISTINCT or aggregation, that is not OK. Example:
   * MATCH (a) RETURN sum(a.age) ORDER BY a.name
   *
   * This method takes the "Variable not defined" errors we get from the semantic analysis and provides a more helpful
   * error message
   * @param previousScopeVars all variables defined in the previous scope.
   * @param error the error
   * @return an error with a possibly better error message
   */
  private[ast] def warnOnAccessToRestrictedVariableInOrderByOrWhere(previousScopeVars: Set[String])(
    error: SemanticErrorDef
  ): SemanticErrorDef = {
    previousScopeVars.collectFirst {
      case name if error.msg.equals(s"Variable `$name` not defined") =>
        error.withMsg(
          s"In a WITH/RETURN with DISTINCT or an aggregation, it is not possible to access variables declared before the WITH/RETURN: $name"
        )
    }.getOrElse(error)
  }

  // use an empty state when checking skip & limit, as these have entirely isolated context
  private def checkSkip: SemanticCheck = withState(SemanticState.clean)(skip.semanticCheck)
  private def checkLimit: SemanticCheck = withState(SemanticState.clean)(limit.semanticCheck)

  def verifyOrderByAggregationUse(fail: (String, InputPosition) => Nothing): Unit = {
    val aggregationInProjection = returnItems.containsAggregate
    val aggregationInOrderBy = orderBy.exists(_.sortItems.map(_.expression).exists(containsAggregate))
    if (!aggregationInProjection && aggregationInOrderBy)
      fail(s"Cannot use aggregation in ORDER BY if there are no aggregate expressions in the preceding $name", position)
  }
}

// used for SHOW/TERMINATE commands
sealed trait WithType
case object DefaultWith extends WithType
case object ParsedAsYield extends WithType
case object AddedInRewrite extends WithType

object With {

  def apply(returnItems: ReturnItems)(pos: InputPosition): With =
    With(distinct = false, returnItems, None, None, None, None)(pos)
}

case class With(
  distinct: Boolean,
  returnItems: ReturnItems,
  orderBy: Option[OrderBy],
  skip: Option[Skip],
  limit: Option[Limit],
  where: Option[Where],
  withType: WithType = DefaultWith
)(val position: InputPosition) extends ProjectionClause {

  override def name = "WITH"

  override def clauseSpecificSemanticCheck: SemanticCheck =
    super.clauseSpecificSemanticCheck chain
      ProjectionClause.checkAliasedReturnItems(returnItems, name) chain
      SemanticPatternCheck.checkValidPropertyKeyNamesInReturnItems(returnItems)

  override def withReturnItems(items: Seq[ReturnItem]): With =
    this.copy(returnItems = ReturnItems(returnItems.includeExisting, items)(returnItems.position))(this.position)
}

object Return {

  def apply(returnItems: ReturnItems)(pos: InputPosition): Return =
    Return(distinct = false, returnItems, None, None, None)(pos)
}

case class Return(
  distinct: Boolean,
  returnItems: ReturnItems,
  orderBy: Option[OrderBy],
  skip: Option[Skip],
  limit: Option[Limit],
  excludedNames: Set[String] = Set.empty,
  addedInRewrite: Boolean = false // used for SHOW/TERMINATE commands
)(val position: InputPosition) extends ProjectionClause with ClauseAllowedOnSystem {

  override def name = "RETURN"

  override def isReturn: Boolean = true

  override def where: Option[Where] = None

  override def returnVariables: ReturnVariables = returnItems.returnVariables

  override def clauseSpecificSemanticCheck: SemanticCheck =
    super.clauseSpecificSemanticCheck chain
      checkVariableScope chain
      ProjectionClause.checkAliasedReturnItems(returnItems, "CALL { RETURN ... }") chain
      SemanticPatternCheck.checkValidPropertyKeyNamesInReturnItems(returnItems)

  override def withReturnItems(items: Seq[ReturnItem]): Return =
    this.copy(returnItems = ReturnItems(returnItems.includeExisting, items)(returnItems.position))(this.position)

  def withReturnItems(returnItems: ReturnItems): Return =
    this.copy(returnItems = returnItems)(this.position)

  private def checkVariableScope: SemanticState => Seq[SemanticError] = s =>
    returnItems match {
      case ReturnItems(star, _, _) if star && s.currentScope.isEmpty =>
        Seq(SemanticError("RETURN * is not allowed when there are no variables in scope", position))
      case _ =>
        Seq.empty
    }
}

case class Yield(
  returnItems: ReturnItems,
  orderBy: Option[OrderBy],
  skip: Option[Skip],
  limit: Option[Limit],
  where: Option[Where]
)(val position: InputPosition) extends ProjectionClause with ClauseAllowedOnSystem {
  override def distinct: Boolean = false

  override def name: String = "YIELD"

  override def withReturnItems(items: Seq[ReturnItem]): Yield =
    this.copy(returnItems = ReturnItems(returnItems.includeExisting, items)(returnItems.position))(this.position)

  def withReturnItems(returnItems: ReturnItems): Yield =
    this.copy(returnItems = returnItems)(this.position)

  override def warnOnAccessToRestrictedVariableInOrderByOrWhere(previousScopeVars: Set[String])(error: SemanticErrorDef)
    : SemanticErrorDef = error
}

object SubqueryCall {

  final case class InTransactionsBatchParameters(batchSize: Expression)(val position: InputPosition) extends ASTNode
      with SemanticCheckable {

    override def semanticCheck: SemanticCheck =
      checkExpressionIsStaticInt(batchSize, "OF ... ROWS", acceptsZero = false)
  }

  final case class InTransactionsReportParameters(reportAs: LogicalVariable)(val position: InputPosition)
      extends ASTNode with SemanticCheckable with SemanticAnalysisTooling {

    override def semanticCheck: SemanticCheck =
      declareVariable(reportAs, CTMap) chain specifyType(CTMap, reportAs)
  }

  final case class InTransactionsErrorParameters(behaviour: InTransactionsOnErrorBehaviour)(
    val position: InputPosition
  ) extends ASTNode

  sealed trait InTransactionsOnErrorBehaviour

  object InTransactionsOnErrorBehaviour {
    case object OnErrorContinue extends InTransactionsOnErrorBehaviour
    case object OnErrorBreak extends InTransactionsOnErrorBehaviour
    case object OnErrorFail extends InTransactionsOnErrorBehaviour
  }

  final case class InTransactionsParameters private (
    batchParams: Option[InTransactionsBatchParameters],
    errorParams: Option[InTransactionsErrorParameters],
    reportParams: Option[InTransactionsReportParameters]
  )(val position: InputPosition) extends ASTNode with SemanticCheckable {

    override def semanticCheck: SemanticCheck = {
      val checkBatchParams = batchParams.foldSemanticCheck(_.semanticCheck)
      val checkReportParams = reportParams.foldSemanticCheck(_.semanticCheck)

      val checkErrorReportCombination: SemanticCheck = (errorParams, reportParams) match {
        case (None, Some(reportParams)) =>
          error(
            "REPORT STATUS can only be used when specifying ON ERROR CONTINUE or ON ERROR BREAK",
            reportParams.position
          )
        case (Some(InTransactionsErrorParameters(OnErrorFail)), Some(reportParams)) =>
          error(
            "REPORT STATUS can only be used when specifying ON ERROR CONTINUE or ON ERROR BREAK",
            reportParams.position
          )
        case _ => SemanticCheck.success
      }

      checkBatchParams chain checkReportParams chain checkErrorReportCombination
    }
  }

  def isTransactionalSubquery(clause: SubqueryCall): Boolean = clause.inTransactionsParameters.isDefined

  def findTransactionalSubquery(node: ASTNode): Option[SubqueryCall] =
    node.folder.treeFind[SubqueryCall] { case s if isTransactionalSubquery(s) => true }
}

case class SubqueryCall(innerQuery: Query, inTransactionsParameters: Option[SubqueryCall.InTransactionsParameters])(
  val position: InputPosition
) extends HorizonClause with SemanticAnalysisTooling {

  override def name: String = "CALL"

  override def clauseSpecificSemanticCheck: SemanticCheck = {
    checkSubquery chain
      inTransactionsParameters.foldSemanticCheck {
        _.semanticCheck chain
          checkNoNestedCallInTransactions
      } chain
      checkNoCallInTransactionsInsideRegularCall
  }

  def reportParams: Option[SubqueryCall.InTransactionsReportParameters] =
    inTransactionsParameters.flatMap(_.reportParams)

  def checkSubquery: SemanticCheck = {
    for {
      outerStateWithImports <- innerQuery.checkImportingWith
      // Create empty scope under root
      _ <- SemanticCheck.setState(outerStateWithImports.state.newBaseScope)
      // Check inner query. Allow it to import from outer scope
      innerChecked <- innerQuery.semanticCheckInSubqueryContext(outerStateWithImports.state)
      _ <- returnToOuterScope(outerStateWithImports.state.currentScope)
      // Declare variables that are in output from subquery
      merged <- declareOutputVariablesInOuterScope(innerChecked.state.currentScope.scope)
    } yield {
      val importingWithErrors = outerStateWithImports.errors

      // Avoid double errors if inner has errors
      val allErrors = importingWithErrors ++
        (if (innerChecked.errors.nonEmpty) innerChecked.errors else merged.errors)

      // Keep errors from inner check and from variable declarations
      SemanticCheckResult(merged.state, allErrors)
    }
  }

  private def returnToOuterScope(outerScopeLocation: SemanticState.ScopeLocation): SemanticCheck = {
    SemanticCheck.fromFunction { innerState =>
      val innerCurrentScope = innerState.currentScope.scope

      // Keep working from the latest state
      val after: SemanticState = innerState
        // but jump back to scope tree of outerStateWithImports
        .copy(currentScope = outerScopeLocation)
        // Copy in the scope tree from inner query (needed for Namespacer)
        .insertSiblingScope(innerCurrentScope)
        // Import variables from scope before subquery
        .newSiblingScope
        .importValuesFromScope(outerScopeLocation.scope)

      SemanticCheckResult.success(after)
    }
  }

  override def semanticCheckContinuation(previousScope: Scope, outerScope: Option[Scope] = None): SemanticCheck = {
    s: SemanticState =>
      SemanticCheckResult(s.importValuesFromScope(previousScope), Vector())
  }

  private def declareOutputVariablesInOuterScope(rootScope: Scope): SemanticCheck = {
    when(innerQuery.isReturning) {
      val scopeForDeclaringVariables = innerQuery.finalScope(rootScope)
      declareVariables(scopeForDeclaringVariables.symbolTable.values)
    }
  }

  private def checkNoNestedCallInTransactions: SemanticCheck = {
    val nestedCallInTransactions = SubqueryCall.findTransactionalSubquery(innerQuery)
    nestedCallInTransactions.foldSemanticCheck { nestedCallInTransactions =>
      error("Nested CALL { ... } IN TRANSACTIONS is not supported", nestedCallInTransactions.position)
    }
  }

  private def checkNoCallInTransactionsInsideRegularCall: SemanticCheck = {
    val nestedCallInTransactions =
      if (inTransactionsParameters.isEmpty) {
        SubqueryCall.findTransactionalSubquery(innerQuery)
      } else
        None

    nestedCallInTransactions.foldSemanticCheck { nestedCallInTransactions =>
      error("CALL { ... } IN TRANSACTIONS nested in a regular CALL is not supported", nestedCallInTransactions.position)
    }
  }
}

// Show and terminate command clauses

sealed trait CommandClause extends Clause with SemanticAnalysisTooling {
  def unfilteredColumns: DefaultOrAllShowColumns

  override def clauseSpecificSemanticCheck: SemanticCheck =
    semanticCheckFold(unfilteredColumns.columns)(sc => declareVariable(sc.variable, sc.cypherType))

  def where: Option[Where]

  def moveWhereToYield: CommandClause
}

object CommandClause {

  def unapply(cc: CommandClause): Option[(List[ShowColumn], Option[Where])] =
    Some((cc.unfilteredColumns.columns, cc.where))
}

// For a query to be allowed to run on system it needs to consist of:
// - only ClauseAllowedOnSystem clauses (or the WITH that was parsed as YIELD/added in rewriter for transaction commands)
// - at least one CommandClauseAllowedOnSystem clause
sealed trait ClauseAllowedOnSystem
sealed trait CommandClauseAllowedOnSystem extends ClauseAllowedOnSystem

case class ShowIndexesClause(
  unfilteredColumns: DefaultOrAllShowColumns,
  indexType: ShowIndexType,
  brief: Boolean,
  verbose: Boolean,
  where: Option[Where],
  hasYield: Boolean
)(val position: InputPosition) extends CommandClause {
  override def name: String = "SHOW INDEXES"

  override def moveWhereToYield: CommandClause = copy(where = None, hasYield = true)(position)

  override def clauseSpecificSemanticCheck: SemanticCheck =
    if (brief || verbose)
      error(
        """`SHOW INDEXES` no longer allows the `BRIEF` and `VERBOSE` keywords,
          |please omit `BRIEF` and use `YIELD *` instead of `VERBOSE`.""".stripMargin,
        position
      )
    else if (indexType == BtreeIndexes) error("Invalid index type b-tree, please omit the `BTREE` filter.", position)
    else super.clauseSpecificSemanticCheck
}

object ShowIndexesClause {

  def apply(
    indexType: ShowIndexType,
    brief: Boolean,
    verbose: Boolean,
    where: Option[Where],
    hasYield: Boolean
  )(position: InputPosition): ShowIndexesClause = {
    val briefCols = List(
      ShowColumn("id", CTInteger)(position),
      ShowColumn("name")(position),
      ShowColumn("state")(position),
      ShowColumn("populationPercent", CTFloat)(position),
      ShowColumn("type")(position),
      ShowColumn("entityType")(position),
      ShowColumn("labelsOrTypes", CTList(CTString))(position),
      ShowColumn("properties", CTList(CTString))(position),
      ShowColumn("indexProvider")(position),
      ShowColumn("owningConstraint")(position),
      ShowColumn("lastRead", CTDateTime)(position),
      ShowColumn("readCount", CTInteger)(position)
    )
    val verboseCols = List(
      ShowColumn("trackedSince", CTDateTime)(position),
      ShowColumn("options", CTMap)(position),
      ShowColumn("failureMessage")(position),
      ShowColumn("createStatement")(position)
    )

    ShowIndexesClause(
      DefaultOrAllShowColumns(hasYield, briefCols, briefCols ++ verboseCols),
      indexType,
      brief,
      verbose,
      where,
      hasYield
    )(position)
  }
}

case class ShowConstraintsClause(
  unfilteredColumns: DefaultOrAllShowColumns,
  constraintType: ShowConstraintType,
  brief: Boolean,
  verbose: Boolean,
  where: Option[Where],
  hasYield: Boolean
)(val position: InputPosition) extends CommandClause {
  override def name: String = "SHOW CONSTRAINTS"

  override def moveWhereToYield: CommandClause = copy(where = None, hasYield = true)(position)

  val existsErrorMessage =
    "`SHOW CONSTRAINTS` no longer allows the `EXISTS` keyword, please use `EXIST` or `PROPERTY EXISTENCE` instead."

  override def clauseSpecificSemanticCheck: SemanticCheck = constraintType match {
    case ExistsConstraints(RemovedSyntax)     => error(existsErrorMessage, position)
    case NodeExistsConstraints(RemovedSyntax) => error(existsErrorMessage, position)
    case RelExistsConstraints(RemovedSyntax)  => error(existsErrorMessage, position)
    case _ if brief || verbose =>
      error(
        """`SHOW CONSTRAINTS` no longer allows the `BRIEF` and `VERBOSE` keywords,
          |please omit `BRIEF` and use `YIELD *` instead of `VERBOSE`.""".stripMargin,
        position
      )
    case _ => super.clauseSpecificSemanticCheck
  }
}

object ShowConstraintsClause {

  def apply(
    constraintType: ShowConstraintType,
    brief: Boolean,
    verbose: Boolean,
    where: Option[Where],
    hasYield: Boolean
  )(position: InputPosition): ShowConstraintsClause = {
    val briefCols = List(
      ShowColumn("id", CTInteger)(position),
      ShowColumn("name")(position),
      ShowColumn("type")(position),
      ShowColumn("entityType")(position),
      ShowColumn("labelsOrTypes", CTList(CTString))(position),
      ShowColumn("properties", CTList(CTString))(position),
      ShowColumn("ownedIndex")(position),
      ShowColumn("propertyType")(position)
    )
    val verboseCols = List(
      ShowColumn("options", CTMap)(position),
      ShowColumn("createStatement")(position)
    )

    ShowConstraintsClause(
      DefaultOrAllShowColumns(hasYield, briefCols, briefCols ++ verboseCols),
      constraintType,
      brief,
      verbose,
      where,
      hasYield
    )(position)
  }
}

case class ShowProceduresClause(
  unfilteredColumns: DefaultOrAllShowColumns,
  executable: Option[ExecutableBy],
  where: Option[Where],
  hasYield: Boolean
)(val position: InputPosition) extends CommandClause with CommandClauseAllowedOnSystem {
  override def name: String = "SHOW PROCEDURES"

  override def moveWhereToYield: CommandClause = copy(where = None, hasYield = true)(position)
}

object ShowProceduresClause {

  def apply(
    executable: Option[ExecutableBy],
    where: Option[Where],
    hasYield: Boolean
  )(position: InputPosition): ShowProceduresClause = {
    val briefCols = List(
      ShowColumn("name")(position),
      ShowColumn("description")(position),
      ShowColumn("mode")(position),
      ShowColumn("worksOnSystem", CTBoolean)(position)
    )
    val verboseCols = List(
      ShowColumn("signature")(position),
      ShowColumn("argumentDescription", CTList(CTMap))(position),
      ShowColumn("returnDescription", CTList(CTMap))(position),
      ShowColumn("admin", CTBoolean)(position),
      ShowColumn("rolesExecution", CTList(CTString))(position),
      ShowColumn("rolesBoostedExecution", CTList(CTString))(position),
      ShowColumn("isDeprecated", CTBoolean)(position),
      ShowColumn("option", CTMap)(position)
    )

    ShowProceduresClause(
      DefaultOrAllShowColumns(hasYield, briefCols, briefCols ++ verboseCols),
      executable,
      where,
      hasYield
    )(position)
  }
}

case class ShowFunctionsClause(
  unfilteredColumns: DefaultOrAllShowColumns,
  functionType: ShowFunctionType,
  executable: Option[ExecutableBy],
  where: Option[Where],
  hasYield: Boolean
)(val position: InputPosition) extends CommandClause with CommandClauseAllowedOnSystem {
  override def name: String = "SHOW FUNCTIONS"

  override def moveWhereToYield: CommandClause = copy(where = None, hasYield = true)(position)
}

object ShowFunctionsClause {

  def apply(
    functionType: ShowFunctionType,
    executable: Option[ExecutableBy],
    where: Option[Where],
    hasYield: Boolean
  )(position: InputPosition): ShowFunctionsClause = {
    val briefCols = List(
      ShowColumn("name")(position),
      ShowColumn("category")(position),
      ShowColumn("description")(position)
    )
    val verboseCols = List(
      ShowColumn("signature")(position),
      ShowColumn("isBuiltIn", CTBoolean)(position),
      ShowColumn("argumentDescription", CTList(CTMap))(position),
      ShowColumn("returnDescription")(position),
      ShowColumn("aggregating", CTBoolean)(position),
      ShowColumn("rolesExecution", CTList(CTString))(position),
      ShowColumn("rolesBoostedExecution", CTList(CTString))(position),
      ShowColumn("isDeprecated", CTBoolean)(position)
    )

    ShowFunctionsClause(
      DefaultOrAllShowColumns(hasYield, briefCols, briefCols ++ verboseCols),
      functionType,
      executable,
      where,
      hasYield
    )(position)
  }
}

sealed trait TransactionsCommandClause extends CommandClause with CommandClauseAllowedOnSystem {
  // Original columns before potential rename or filtering in YIELD
  def transactionColumns: List[TransactionColumn]

  // Yielded columns or yield *
  def yieldItems: List[CommandResultItem]
  def yieldAll: Boolean

  // Transaction ids, either:
  // - a list of strings
  // - a single expression resolving to a single string or a list of strings
  def ids: Either[List[String], Expression]

  // Semantic check:
  private lazy val columnsMap: Map[String, CypherType] =
    transactionColumns.map(column => column.name -> column.cypherType).toMap[String, CypherType]

  private def checkYieldItems: SemanticCheck =
    if (yieldItems.nonEmpty) yieldItems.foldSemanticCheck(_.semanticCheck(columnsMap))
    else super.clauseSpecificSemanticCheck

  override def clauseSpecificSemanticCheck: SemanticCheck = ids match {
    case Right(e) => SemanticExpressionCheck.simple(e) chain checkYieldItems
    case _        => checkYieldItems
  }
}

// Yield columns: keeps track of the original name and the yield variable (either same name or renamed)
case class CommandResultItem(originalName: String, aliasedVariable: LogicalVariable)(val position: InputPosition)
    extends ASTNode with SemanticAnalysisTooling {

  def semanticCheck(columns: Map[String, CypherType]): SemanticCheck =
    columns
      .get(originalName)
      .map { typ => declareVariable(aliasedVariable, typ): SemanticCheck }
      .getOrElse(error(s"Trying to YIELD non-existing column: `$originalName`", position))
}

// Column name together with the column type
// Used to create the ShowColumns but without keeping variables
// (as having undeclared variables in the ast caused issues with namespacer)
case class TransactionColumn(name: String, cypherType: CypherType = CTString)

case class ShowTransactionsClause(
  briefTransactionColumns: List[TransactionColumn],
  allTransactionColumns: List[TransactionColumn],
  ids: Either[List[String], Expression],
  where: Option[Where],
  yieldItems: List[CommandResultItem],
  yieldAll: Boolean
)(val position: InputPosition) extends TransactionsCommandClause {
  override def name: String = "SHOW TRANSACTIONS"

  private val useAllColumns = yieldItems.nonEmpty || yieldAll

  val transactionColumns: List[TransactionColumn] =
    if (useAllColumns) allTransactionColumns else briefTransactionColumns

  private val briefColumns = briefTransactionColumns.map(c => ShowColumn(c.name, c.cypherType)(position))
  private val allColumns = allTransactionColumns.map(c => ShowColumn(c.name, c.cypherType)(position))

  val unfilteredColumns: DefaultOrAllShowColumns =
    DefaultOrAllShowColumns(useAllColumns, briefColumns, allColumns)

  override def moveWhereToYield: CommandClause = copy(where = None)(position)
}

object ShowTransactionsClause {

  def apply(
    ids: Either[List[String], Expression],
    where: Option[Where],
    yieldItems: List[CommandResultItem],
    yieldAll: Boolean
  )(position: InputPosition): ShowTransactionsClause = {
    val columns = List(
      // (column, brief)
      (TransactionColumn("database"), true),
      (TransactionColumn("transactionId"), true),
      (TransactionColumn("currentQueryId"), true),
      (TransactionColumn("outerTransactionId"), false),
      (TransactionColumn("connectionId"), true),
      (TransactionColumn("clientAddress"), true),
      (TransactionColumn("username"), true),
      (TransactionColumn("metaData", CTMap), false),
      (TransactionColumn("currentQuery"), true),
      (TransactionColumn("parameters", CTMap), false),
      (TransactionColumn("planner"), false),
      (TransactionColumn("runtime"), false),
      (TransactionColumn("indexes", CTList(CTMap)), false),
      (TransactionColumn("startTime"), true),
      (TransactionColumn("currentQueryStartTime"), false),
      (TransactionColumn("protocol"), false),
      (TransactionColumn("requestUri"), false),
      (TransactionColumn("status"), true),
      (TransactionColumn("currentQueryStatus"), false),
      (TransactionColumn("statusDetails"), false),
      (TransactionColumn("resourceInformation", CTMap), false),
      (TransactionColumn("activeLockCount", CTInteger), false),
      (TransactionColumn("currentQueryActiveLockCount", CTInteger), false),
      (TransactionColumn("elapsedTime", CTDuration), true),
      (TransactionColumn("cpuTime", CTDuration), false),
      (TransactionColumn("waitTime", CTDuration), false),
      (TransactionColumn("idleTime", CTDuration), false),
      (TransactionColumn("currentQueryElapsedTime", CTDuration), false),
      (TransactionColumn("currentQueryCpuTime", CTDuration), false),
      (TransactionColumn("currentQueryWaitTime", CTDuration), false),
      (TransactionColumn("currentQueryIdleTime", CTDuration), false),
      (TransactionColumn("currentQueryAllocatedBytes", CTInteger), false),
      (TransactionColumn("allocatedDirectBytes", CTInteger), false),
      (TransactionColumn("estimatedUsedHeapMemory", CTInteger), false),
      (TransactionColumn("pageHits", CTInteger), false),
      (TransactionColumn("pageFaults", CTInteger), false),
      (TransactionColumn("currentQueryPageHits", CTInteger), false),
      (TransactionColumn("currentQueryPageFaults", CTInteger), false),
      (TransactionColumn("initializationStackTrace"), false)
    )
    val briefColumns = columns.filter(_._2).map(_._1)
    val allColumns = columns.map(_._1)

    ShowTransactionsClause(
      briefColumns,
      allColumns,
      ids,
      where,
      yieldItems,
      yieldAll
    )(position)
  }
}

case class TerminateTransactionsClause(
  transactionColumns: List[TransactionColumn],
  ids: Either[List[String], Expression],
  yieldItems: List[CommandResultItem],
  yieldAll: Boolean,
  wherePos: Option[InputPosition]
)(val position: InputPosition) extends TransactionsCommandClause {
  override def name: String = "TERMINATE TRANSACTIONS"

  private val columns = transactionColumns.map(c => ShowColumn(c.name, c.cypherType)(position))

  val unfilteredColumns: DefaultOrAllShowColumns =
    DefaultOrAllShowColumns(useAllColumns = yieldItems.nonEmpty || yieldAll, columns, columns)

  override def clauseSpecificSemanticCheck: SemanticCheck = when(ids match {
    case Left(ls) => ls.size < 1
    case Right(_) => false // expression list length needs to be checked at runtime
  }) {
    error("Missing transaction id to terminate, the transaction id can be found using `SHOW TRANSACTIONS`", position)
  } chain when(wherePos.isDefined) {
    error(
      "`WHERE` is not allowed by itself, please use `TERMINATE TRANSACTION ... YIELD ... WHERE ...` instead",
      wherePos.get
    )
  } chain super.clauseSpecificSemanticCheck

  override def where: Option[Where] = None
  override def moveWhereToYield: CommandClause = this
}

object TerminateTransactionsClause {

  def apply(
    ids: Either[List[String], Expression],
    yieldItems: List[CommandResultItem],
    yieldAll: Boolean,
    wherePos: Option[InputPosition]
  )(position: InputPosition): TerminateTransactionsClause = {
    // All columns are currently default
    val columns = List(
      TransactionColumn("transactionId"),
      TransactionColumn("username"),
      TransactionColumn("message")
    )

    TerminateTransactionsClause(
      columns,
      ids,
      yieldItems,
      yieldAll,
      wherePos
    )(position)
  }
}

case class ShowSettingsClause(
  unfilteredColumns: DefaultOrAllShowColumns,
  names: Either[List[String], Expression],
  where: Option[Where],
  hasYield: Boolean
)(val position: InputPosition) extends CommandClause with CommandClauseAllowedOnSystem {

  override def name: String = "SHOW SETTINGS"

  override def moveWhereToYield: CommandClause = copy(where = None, hasYield = true)(position)

  private def expressionCheck: SemanticCheck = names match {
    case Right(e) => SemanticExpressionCheck.simple(e)
    case _        => SemanticCheck.success
  }

  override def clauseSpecificSemanticCheck: SemanticCheck = {
    requireFeatureSupport(
      s"The `$name` clause",
      SemanticFeature.ShowSetting,
      position
    ) chain expressionCheck chain super.clauseSpecificSemanticCheck
  }
}

object ShowSettingsClause {

  def apply(
    names: Either[List[String], Expression],
    where: Option[Where],
    hasYield: Boolean
  )(position: InputPosition): ShowSettingsClause = {
    val defaultCols = List(
      ShowColumn("name")(position),
      ShowColumn("value")(position),
      ShowColumn("isDynamic", CTBoolean)(position),
      ShowColumn("defaultValue")(position),
      ShowColumn("description")(position)
    )
    val verboseCols = List(
      ShowColumn("startupValue")(position),
      ShowColumn("isExplicitlySet", CTBoolean)(position),
      ShowColumn("validValues")(position),
      ShowColumn("isDeprecated", CTBoolean)(position)
    )

    ShowSettingsClause(
      DefaultOrAllShowColumns(hasYield, defaultCols, defaultCols ++ verboseCols),
      names,
      where,
      hasYield
    )(position)
  }
}
