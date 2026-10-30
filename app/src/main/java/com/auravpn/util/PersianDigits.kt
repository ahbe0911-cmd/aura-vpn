package com.auravpn.util
object PersianDigits {
 private val digits="۰۱۲۳۴۵۶۷۸۹"
 fun format(value:String,usePersianDigits:Boolean=true):String {
  if(!usePersianDigits) return value
  return buildString(value.length) { value.forEach { c -> append(if(c in '0'..'9') digits[c-'0'] else c) } }
 }
}
