#!/bin/bash
# install.sh
#
# install keycloak module :
# event-emitter

set -eE


usage ()
{
    echo "usage: $0 keycloak_path [-c] [-t host] [-u]"
}


init()
{
    # deps
    [[ $(xmlstarlet --version) ]] || { echo >&2 "Requires xmlstarlet"; exit 1; }

    #optional args
    argv__CLUSTER=0
    argv__TARGET="http://localhost:8888/event/receiver"
    argv__UNINSTALL=0
    getopt_results=$(getopt -s bash -o ct:u --long cluster,target:,uninstall -- "$@")

    if test $? != 0
    then
        echo "unrecognized option"
        exit 1
    fi
    eval set -- "$getopt_results"

    while true
    do
        case "$1" in
            -u|--uninstall)
                argv__UNINSTALL=1
                echo "--delete set. will remove plugin"
                shift
                ;;
            -c|--cluster)
                argv__CLUSTER=1
                echo "--cluster set. Will edit cluster config"
                shift
                ;;
            -t|--target)
                argv__TARGET="$2"
                echo "--target set to \"$argv__TARGET\". Will edit the emitter target URI"
                shift 2
                ;;
            --)
                shift
                break
                ;;
            *)
                EXCEPTION=$Main__ParameterException
                EXCEPTION_MSG="unparseable option $1"
                exit 1
                ;;
        esac
    done

    # positional args
    argv__KEYCLOAK=""
    if [[ "$#" -ne 1 ]]; then
        usage
        exit 1
    fi
    argv__KEYCLOAK="$1"
    # optional args
    CONF_FILE=""
    if [[ "$argv__CLUSTER" -eq 1 ]]; then
        CONF_FILE=$argv__KEYCLOAK/standalone/configuration/standalone-ha.xml
    else
        CONF_FILE=$argv__KEYCLOAK/standalone/configuration/standalone.xml
    fi
    echo $CONF_FILE
    MODULE_NAME=$(xmlstarlet sel -N oe="urn:jboss:module:1.3" -t -v '/oe:module/@name' -n module.xml)
    MODULE=${MODULE_NAME##*.}
    JAR_PATH=`find ./target/ -type f -name "*.jar" -not -name "*sources.jar"`
    JAR_NAME=`basename $JAR_PATH`
    MODULE_PATH=${MODULE_NAME//./\/}/main
}

init_exceptions()
{
    EXCEPTION=0
    EXCEPTION_MSG=""
    #Main__Default_Unkown=1
    Main__ParameterException=2
}

cleanup()
{
    #clean dir structure in case of script failure
    echo "cleanup..."
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -d "/_:server/_:profile/c:subsystem/c:providers/c:provider[text()='module:$MODULE_NAME']" $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -d "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider[@name='event-emitter']" $CONF_FILE
    sed -i "$ s/,$MODULE$//" $argv__KEYCLOAK/modules/layers.conf
    rm -rf $argv__KEYCLOAK/modules/system/layers/$MODULE
    echo "done"
}

Main__interruptHandler()
{
    # @description signal handler for SIGINT
    echo "$0: SIGINT caught"
    exit
}
Main__terminationHandler()
{
    # @description signal handler for SIGTERM
    echo "$0: SIGTERM caught"
    exit
}
Main__exitHandler()
{
    cleanup
    if [[ "$EXCEPTION" -ne 0 ]] ; then
        echo "$0: error : ${EXCEPTION_MSG}"
    fi
    exit
}

trap Main__interruptHandler INT
trap Main__terminationHandler TERM
trap Main__exitHandler ERR

Main__main()
{
    # init script temporals
    init_exceptions
    init "$@"
    if [[ "$argv__UNINSTALL" -eq 1 ]]; then
        cleanup
        exit 0
    fi
    # install module
    mkdir -p $argv__KEYCLOAK/modules/system/layers/$MODULE/$MODULE_PATH/
    cp $JAR_PATH $argv__KEYCLOAK/modules/system/layers/$MODULE/$MODULE_PATH/
    cp module.xml $argv__KEYCLOAK/modules/system/layers/$MODULE/$MODULE_PATH/
    sed -i "s@JAR_NAME@${JAR_NAME}@g" $argv__KEYCLOAK/modules/system/layers/$MODULE/$MODULE_PATH/module.xml
    if ! grep -q "$MODULE" "$argv__KEYCLOAK/modules/layers.conf"; then
        sed -i "$ s/$/,$MODULE/" $argv__KEYCLOAK/modules/layers.conf
    fi

    # add provider
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem/c:providers" -t elem -n provider -v "module:$MODULE_NAME" $CONF_FILE

    # add SPI configuration for events listeners (if not already there)
    MODULES_EXISTS=`xmlstarlet sel -N c="urn:jboss:domain:keycloak-server:1.1" -t -v "count(/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener'])" $CONF_FILE`
    if [ $MODULES_EXISTS -eq "0" ]; then
        xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem" -t elem -n spi $CONF_FILE
        xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[not(@name)]" -t attr -n 'name' -v 'eventsListener' $CONF_FILE
    fi
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']" -t elem -n provider $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider" -t attr -n name -v 'event-emitter' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider" -t attr -n enabled -v 'true' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider" -t elem -n properties $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties" -t elem -n property $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[not(@*)]" -t attr -n name -v 'format' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[@name='format']" -t attr -n value -v 'FLATBUFFER' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties" -t elem -n property $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[not(@*)]" -t attr -n name -v 'targetUri' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[@name='targetUri']" -t attr -n value -v "$argv__TARGET" $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties" -t elem -n property $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[not(@*)]" -t attr -n name -v 'bufferCapacity' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[@name='bufferCapacity']" -t attr -n value -v '10' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties" -t elem -n property $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[not(@*)]" -t attr -n name -v 'keycloakId' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[@name='keycloakId']" -t attr -n value -v '1' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -s "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties" -t elem -n property $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[not(@*)]" -t attr -n name -v 'datacenterId' $CONF_FILE
    xmlstarlet ed -L -N c="urn:jboss:domain:keycloak-server:1.1" -i "/_:server/_:profile/c:subsystem/c:spi[@name='eventsListener']/c:provider/c:properties/c:property[@name='datacenterId']" -t attr -n value -v '1' $CONF_FILE

    # copy libs to main directory
    cp lib-modules/main/* $argv__KEYCLOAK/modules/system/layers/$MODULE/$MODULE_PATH/

    exit 0
}

Main__main "$@"
