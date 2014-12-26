#!/system/bin/sh
#set -x
set -e

BINDIR=${0%sbs.sh}
LOG=$BINDIR/sbs-log.txt

log () {
    /system/bin/log -t SBS $*
    echo $* >> $LOG
}

# Busybox comands
bbcp ()     { $BINDIR/busybox cp $* ; }
bbdate ()   { $BINDIR/busybox date $* ; }
bbln ()     { $BINDIR/busybox ln $* ; }
bbmd5sum () { $BINDIR/busybox md5sum $1 ; }
bbmv ()     { $BINDIR/busybox mv $* ; }
bbpidof ()  { $BINDIR/busybox pidof $* ; }
bbrm ()     { $BINDIR/busybox rm $* ; }
bbchmod()   { $BINDIR/busybox chmod $* ; }

# Built in commands
bimount ()  { mount $* ; }
bichown ()  { chown $* ; }


# Wrappers for busybox
bbtouch () {
    $BINDIR/busybox touch $1
    bichown system.system $1
}
bbmkdir () {
    $BINDIR/busybox mkdir -p $1
    bichown system.system $1
}

# Add properties and md5sum of libsurfaceflinger if we haven't done that before
if [ ! -e $BINDIR/log-has-deviceinfo ] ; then
    bbtouch $BINDIR/log-has-deviceinfo
    getprop  > $LOG  || true
    bbmd5sum /system/lib/libsurfaceflinger.so >> $LOG
fi
# Make sure the log is readable by the email client (and everyone else)
test -e $LOG && bbchmod 0666 $LOG
# Redirect all output to the log file
#exec 1>> $LOG 2>&1

log "============="
log "$((bbdate))"
log "ARGS: $*"

BB=$BINDIR/busybox

VER=$(getprop ro.build.version.release)
CMVER=$(getprop ro.cm.version)

log "VER:$VER CMVER:$CMVER"
echo "$VER"   | grep "^4.4" > /dev/null && IS44=1
echo "$VER"   | grep "^5.0" > /dev/null && IS50=1
echo "$CMVER" | grep "^11"  > /dev/null && ISCM11=1

log "Got IS44=$IS44 IS50=$IS50 ISCM11=$ISCM11"
if [ -z "$VARIANT" ] ; then
    if [ "$ISCM11" = "1" ] ; then
	  VARIANT=cm11m9
    elif [ "$IS44" = "1" ] ; then
	  VARIANT=aosp444-102
    elif [ "$IS50" = "1" ] ; then
	  VARIANT=aosp500
    else
	    log "No matching shared library"
	    exit 108
    fi
fi

log "Selected variant: $VARIANT"

test -e $BB || ( log "Failed to copy busybox to $BB" 2>&1 ; exit 104 )

function cmd_install () {
    if [ -e /system/bin/surfaceflinger.real ] ; then
        log "/system/bin/surfaceflinger.real already exists, please uninstall first"
        exit 1
    fi
    bbmkdir /data/system/sbs

    log "Modifying /system"
    bimount -o remount,rw /system

    log "Installing copying selected ($VARIANT) binaries into /system/lib/sbs"
    bbmkdir /system/lib/sbs
    bbcp /data/data/com.frma.sbs/files/libsurfaceflinger-$VARIANT.so /system/lib/sbs/libsurfaceflinger.so
    bbcp /data/data/com.frma.sbs/files/surfaceflinger-$VARIANT       /system/lib/sbs/surfaceflinger
    bbchmod 644 /system/lib/sbs/libsurfaceflinger.so
    bbchmod 755 /system/lib/sbs/surfaceflinger

    log "Replacing /system/bin/surfaceflinger with wrapper"
    bbcp /system/bin/surfaceflinger /data/data/com.frma.sbs/files/surfaceflinger.org
    bbmv /system/bin/surfaceflinger /system/bin/surfaceflinger.real
    bbcp /data/data/com.frma.sbs/files/surfaceflinger /system/bin/surfaceflinger
    bbchmod 755 /system/bin/surfaceflinger
    bimount -o remount,ro /system
    log "Done modifying system"
}

function cmd_uninstall () {
    if [ ! -e /system/bin/surfaceflinger.real ] ; then
        log "/system/bin/surfaceflinger.real not found, can't uninstall"
    else
        rm -rf /data/system/sbs
        bimount -o remount,rw /system
        mv /system/bin/surfaceflinger.real /system/bin/surfaceflinger
        rm -rf /system/lib/sbs
        bimount -o remount,ro /system
    fi
}

function cmd_isinstalled () {
        if [ -e /system/bin/surfaceflinger.real ] ; then
            log "Result: Yes"
            return 0
        else
            log "Result: No"
            return 1
        fi
}

function cmd_enable () {
    bbmkdir /data/system/sbs
    bbtouch /data/system/sbs/enabled
}

function cmd_enable_permanent () {
    bbmkdir /data/system/sbs
    bbtouch /data/system/sbs/permanent
}

function cmd_disable () {
    bbrm -f /data/system/sbs/enabled
    bbrm -f /data/system/sbs/permanent
}

function cmd_ispermanent () {
    if [ -e /data/system/sbs/permanent ] ; then
        log "Result: Yes"
        return 0
    else
        log "Result: No"
        return 1
    fi
}

function cmd_isenabled () {
    if [ -e /data/system/sbs/enabled ] ; then
        log "Result: Yes"
        return 0
    else
        log "Result: No"
        return 1
    fi
}

function cmd_isloaded () {
    if grep /system/lib/sbs /proc/$(bbpidof surfaceflinger.real)/maps > /dev/null 2>&1 ; then
        log "Result: Yes"
        return 0
    else
        log "Result: No"
        return 1
    fi
}

function cmd_reboot () {
    sync
    ( stop ; reboot ) &
}

function cmd_set () {
        FLAGS=$1
        ZOOM=$2
        IMGDIST=$3
        R=$(service call SurfaceFlinger 4711 i32 $FLAGS i32 $ZOOM i32 $IMGDIST)
	    log "Result: $R"
	    echo "$R" | grep "^Result: Parcel(NULL)" && ( echo "Ok" ; exit 0 ) || ( log "Not installed" ; exit 106)
}
case $1 in
    install)
        cmd_install
        cmd_reboot
        ;;
    uninstall)
        cmd_uninstall
        cmd_reboot
        ;;
    isinstalled)
   	    cmd_isinstalled
    	;;
    enable)
        cmd_enable
        ;;
    enablepermanent)
        cmd_enable_permanent
        ;;
    disable)
        cmd_disable
        ;;
    ispermanent)
        cmd_ispermanent
        ;;
    isenabled)
        cmd_isenabled
        ;;
    isloaded)
        cmd_isloaded
	;;
    reboot)
	    cmd_reboot
	;;
    set)
        cmd_set $2 $3 $4
        ;;
    *)
	    log "SBS: Unknown subcommand: $1"
	    exit 107
	;;
esac
exit $?


