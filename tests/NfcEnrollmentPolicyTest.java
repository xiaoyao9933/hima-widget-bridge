package com.eric.himawidgetbridge;
public final class NfcEnrollmentPolicyTest {
 private static int checks;
 private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
 public static void main(String[] args){
  byte[] uid={4,10,11,12}, token=new byte[16];token[0]=42;
  NfcEnrollmentPolicy flow=new NfcEnrollmentPolicy(uid,token);
  check(flow.writeFinished(true,false)==NfcEnrollmentPolicy.RETAP,"successful write with empty readback must request independent verification");
  check(flow.verify(new byte[]{4,99},token)==NfcEnrollmentPolicy.WRONG_TAG,"different tag must not finish verification");
  check(flow.verify(uid,token)==NfcEnrollmentPolicy.VERIFIED,"matching fresh read verifies exact pending write");
  check(flow.verify(uid,token)==NfcEnrollmentPolicy.IGNORED,"a completed verification cannot complete twice");
  flow=new NfcEnrollmentPolicy(uid,token);
  check(flow.writeFinished(true,true)==NfcEnrollmentPolicy.VERIFIED,"successful immediate read keeps two-touch path");
  check(flow.verify(uid,token)==NfcEnrollmentPolicy.IGNORED,"immediate completion cannot verify again");
  flow=new NfcEnrollmentPolicy(uid,token);
  check(flow.writeFinished(false,false)==NfcEnrollmentPolicy.FAILED,"actual write exception cannot be rescued by retap");
  check(flow.verify(uid,token)==NfcEnrollmentPolicy.IGNORED,"failed write cannot later bind a tag");
  flow=new NfcEnrollmentPolicy(uid,token);flow.writeFinished(true,false);
  check(flow.verify(uid,null)==NfcEnrollmentPolicy.FAILED,"empty independent read cannot register");
  flow=new NfcEnrollmentPolicy(uid,token);flow.writeFinished(true,false);byte[] different=token.clone();different[1]=1;
  check(flow.verify(uid,different)==NfcEnrollmentPolicy.FAILED,"mismatched marker cannot register");
  flow=new NfcEnrollmentPolicy(uid,token);flow.writeFinished(true,false);flow.cancel();
  check(flow.verify(uid,token)==NfcEnrollmentPolicy.IGNORED,"cancel or background invalidation rejects late read");
  flow=new NfcEnrollmentPolicy(uid,token);flow.pauseDuringWrite();
  check(flow.writeFinished(true,false)==NfcEnrollmentPolicy.FAILED,"pause during write cannot resurrect reader on a fast resume");
  flow=new NfcEnrollmentPolicy(uid,token);flow.pauseDuringWrite();
  check(flow.writeFinished(true,true)==NfcEnrollmentPolicy.VERIFIED,"already verified confirmed write may save disabled registration after pause");
  flow=new NfcEnrollmentPolicy(uid,token);uid[1]=7;token[0]=9;flow.writeFinished(true,false);byte[] savedToken=new byte[16];savedToken[0]=42;
  check(flow.verify(new byte[]{4,10,11,12},savedToken)==NfcEnrollmentPolicy.VERIFIED,"pending identity is immutable");
  System.out.println("NfcEnrollmentPolicyTest: "+checks+" checks passed");
 }
}
