package com.eric.himawidgetbridge;
public final class ControlActionTest {
 private static int checks;
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 public static void main(String[] args){
  check(ControlAction.LOCK.equals(ControlAction.resolve(null,false)),"legacy shortcut still locks");
  check(ControlAction.TRUNK.equals(ControlAction.resolve("trunk",true)),"explicit trunk request stays trunk");
  check(ControlAction.LOCK.equals(ControlAction.resolve("lock",true)),"explicit lock request stays lock");
  check(ControlAction.resolve(null,true)==null,"wrong extra type cannot fall back to lock");
  check(ControlAction.resolve("front_trunk",true)==null,"front trunk is not supported");
  check(ControlAction.resolve("TRUNK",true)==null,"unknown action rejects");
  check(ControlAction.resolve("",true)==null,"empty explicit action rejects");
  check(ControlAction.resolve(" trunk",true)==null,"ambiguous action rejects");
  check(NfcPolicy.MIME.equals(ControlAction.mime("lock")),"legacy MIME unchanged");
  check("application/vnd.eric.hima.trunk".equals(ControlAction.mime("trunk")),"trunk has distinct MIME");
  check(ControlAction.TRUNK.equals(ControlAction.fromMime(ControlAction.mime("trunk"))),"trunk MIME resolves only trunk");
  check(ControlAction.LOCK.equals(ControlAction.fromMime(NfcPolicy.MIME)),"old tags resolve only lock");
  check(ControlAction.fromMime("application/octet-stream")==null,"foreign MIME rejects");
  String[] labels={"车锁","后备箱","前备箱","车窗"};boolean[] available={true,true,true,true};
  check(SlotPolicy.findLock(labels,available)==0,"existing lock matches");
  check(SlotPolicy.findTrunk(labels,available)==1,"trunk does not select lock or front trunk");
  available[1]=false;check(SlotPolicy.findTrunk(labels,available)==-1,"disabled trunk cannot fall back to lock");
  check(SlotPolicy.findTrunk(new String[]{"后备箱","后备箱"},new boolean[]{true,true})==-1,"duplicate trunk refuses");
  check(SlotPolicy.findTrunk(new String[]{"前备箱","开启后备箱"},new boolean[]{true,true})==-1,"only exact supported labels accepted");
  check(SlotPolicy.findTrunk(null,null)==-1,"missing widget refuses");
  System.out.println("ControlActionTest: "+checks+" checks passed");
 }
}
