package org.jetbrains.plugins.scala.lang.psi.impl.expressions {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScInfixExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Infix expression"
  }

  case class ScPrefixExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Prefix expression"
  }
    case class ScPrefixImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Simple prefix"
    }

  case class ScPostfixExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Postfix expression"
  }

  case class ScAssignImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Operator: assign"
  }

  case class ScIfImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Keyword: if"
  }

  case class ScElseImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Keyword: else"
  }

  case class ScWhileImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Keyword: while"
  }

  case class ScReturnImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Keyword: return"
  }

  case class ScThrowImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Keyword: throw"
  }


}