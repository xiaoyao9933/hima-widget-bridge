package com.eric.himawidgetbridge;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Reads real buttons; click checks use attached dummy controls only, never ToggleActivity. */
public final class ControlRoutingInstrumentation extends Instrumentation {
 private int checks;
 private void check(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;}
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle out=new Bundle();Activity home=null;
  try {
   home=startActivitySync(new Intent(getTargetContext(), MainActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   waitForIdleSync(); final Activity page=home;
   runOnMainSync(()->{
    ShortcutManager manager=page.getSystemService(ShortcutManager.class);
    boolean sawLock=false,sawTrunk=false;
    for(ShortcutInfo shortcut:manager.getManifestShortcuts()){
     Intent intent=shortcut.getIntent();
     if("toggle_lock".equals(shortcut.getId())) {sawLock=true;check(ControlAction.LOCK.equals(ControlAction.resolve(intent.getStringExtra(ControlAction.EXTRA),intent.hasExtra(ControlAction.EXTRA))),"legacy manifest shortcut resolves lock");}
     if("trunk".equals(shortcut.getId())) {sawTrunk=true;check(ControlAction.TRUNK.equals(intent.getStringExtra(ControlAction.EXTRA)),"manifest trunk has explicit immutable action");}
    }
    check(sawLock&&sawTrunk,"two published vehicle shortcuts exist");
    check(ControlAction.TRUNK.equals(ShortcutActions.trigger(page,ControlAction.TRUNK).getStringExtra(ControlAction.EXTRA)),"pinned/home trunk intent is trunk");
    check(ControlAction.LOCK.equals(ShortcutActions.trigger(page,ControlAction.LOCK).getStringExtra(ControlAction.EXTRA)),"home lock intent is lock");
    FrameLayout root=new FrameLayout(page); root.setAlpha(0);root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    ((FrameLayout)page.findViewById(android.R.id.content)).addView(root,new FrameLayout.LayoutParams(400,400));
    final int[] clicks={0,0}; TextView lock=new TextView(page);lock.setId(2131430983);lock.setText("车锁");lock.setOnClickListener(v->clicks[0]++);root.addView(lock);
    TextView trunk=new TextView(page);trunk.setId(2131430990);trunk.setText("后备箱");trunk.setOnClickListener(v->clicks[1]++);root.addView(trunk);
    WidgetAdapter.Snapshot state=WidgetAdapter.inspect(root);
    check(state.lock==lock&&state.trunk==trunk,"real adapter separates dummy controls");
    check(state.control(ControlAction.TRUNK).performClick()&&clicks[1]==1&&clicks[0]==0,"trunk dispatch clicks only dummy trunk");
    check(state.control(ControlAction.LOCK).performClick()&&clicks[0]==1&&clicks[1]==1,"lock dispatch clicks only dummy lock");
    trunk.setText("前备箱");check(WidgetAdapter.inspect(root).control(ControlAction.TRUNK)==null,"front trunk cannot match");
    trunk.setText("后备箱");trunk.setEnabled(false);check(WidgetAdapter.inspect(root).control(ControlAction.TRUNK)==null,"unavailable trunk does not fall back");
    check(WidgetAdapter.inspect(root).control("unknown")==null,"unknown operation cannot select lock");
    ((FrameLayout)page.findViewById(android.R.id.content)).removeView(root);
   });
   final WidgetAdapter.Snapshot[] actual={null}; long until=SystemClock.elapsedRealtime()+5000;
   do {runOnMainSync(()->actual[0]=((WidgetPage)page).snapshot()); if(actual[0]!=null&&actual[0].lock!=null&&actual[0].trunk!=null)break;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<until);
   check(actual[0]!=null&&actual[0].lock!=null&&actual[0].trunk!=null,"both original controls recognized in measured hidden host without a click");
   check(((WidgetPage)page).session.bound(),"old widget binding retained");
   check(((WidgetPage)page).session.enabled(),"old lock enable retained");
   check(!((WidgetPage)page).session.enabled(ControlAction.TRUNK),"new trunk is disabled by default");
   out.putInt("checks",checks);out.putBoolean("vehicle_action",false);out.putBoolean("real_widget_read_only",true);out.putBoolean("toggle_activity_started",false);
   runOnMainSync(page::finish);finish(-1,out);
  }catch(Throwable e){out.putInt("checks",checks);out.putString("failure",e.getClass().getSimpleName()+": "+e.getMessage());out.putBoolean("vehicle_action",false);if(home!=null){final Activity page=home;runOnMainSync(page::finish);}finish(0,out);}
 }
}
