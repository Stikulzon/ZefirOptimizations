package ua.zefir.zefiroptimizations.mixin;

import com.ericsson.otp.erlang.*;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;

@Mixin(Vec3d.class)
public abstract class Vec3dMixin {
//    @Inject(method = "add(DDD)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
//    private void add(double x, double y, double z, CallbackInfoReturnable<Vec3d> cir) {
//        try {
//            OtpErlangDouble ex = new OtpErlangDouble(x);
//            OtpErlangDouble ey = new OtpErlangDouble(y);
//            OtpErlangDouble ez = new OtpErlangDouble(z);
//
//            OtpErlangObject[] args = new OtpErlangObject[]{ex, ey, ez};
//            OtpErlangTuple tuple = new OtpErlangTuple(args);
//
//            ZefirOptimizations.getConnection().sendRPC("vec3d", "add", new OtpErlangList(tuple));
//
//            OtpErlangObject result = ZefirOptimizations.getConnection().receiveRPC();
//            if (result instanceof OtpErlangTuple) {
//                OtpErlangTuple resTuple = (OtpErlangTuple) result;
//                double resX = ((OtpErlangDouble) resTuple.elementAt(0)).doubleValue();
//                double resY = ((OtpErlangDouble) resTuple.elementAt(1)).doubleValue();
//                double resZ = ((OtpErlangDouble) resTuple.elementAt(2)).doubleValue();
//
//                cir.setReturnValue(new Vec3d(resX, resY, resZ));
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
