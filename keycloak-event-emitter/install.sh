#!/bin/bash
# install.sh
#
# install keycloak module :
# event-emitter

set -eE
MODULE_DIR=$(dirname $0)
TARGET_DIR=$MODULE_DIR/target
[ -z "$WINDIR" ] && KC_EXE=kc.sh || KC_EXE=kc.bat

usage ()
{
    echo "usage: $0 keycloak_path [-c] [-t host] [-u]"
}

abort_usage_keycloak()
{
  echo "Invalid keycloak path"
  usage
  exit 1
}

init()
{
    #optional args
    argv__TARGET="http://localhost:8811/event/receiver"
    argv__UNINSTALL=0
    getopt_results=$(getopt -s bash -o t:u --long target:,uninstall -- "$@")

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
    [ -d $argv__KEYCLOAK ] && [ -d $argv__KEYCLOAK/bin ] && [ -d $argv__KEYCLOAK/providers ] && [ -d $argv__KEYCLOAK/conf ] || abort_usage_keycloak
    # optional args
    CONF_FILE=$argv__KEYCLOAK/conf/keycloak.conf
    JAR_PATH=`find ${TARGET_DIR} -type f -name "*.jar" -not -name "*sources.jar" | grep -v "archive-tmp"`
    JAR_NAME=`basename $JAR_PATH`
}

init_exceptions()
{
    EXCEPTION=0
    EXCEPTION_MSG=""
    #Main__Default_Unkown=1
    Main__ParameterException=2
}

del_configuration()
{
  if [[ ! -z "$1" ]] ; then
    sed -i "/^$1=/d" ${CONF_FILE}
  fi
}

add_configuration()
{
  if [[ ! -z "$1" ]] ; then
    sed -i "/^$1=/d" ${CONF_FILE}
    echo "$1=$2" >> ${CONF_FILE}
  fi
}

cleanup()
{
    #clean dir structure in case of script failure
    echo "cleanup..."

    del_configuration spi-events-listener-event-emitter-enabled
    del_configuration spi-events-listener-event-emitter-format
    del_configuration spi-events-listener-event-emitter-target-uri
    del_configuration spi-events-listener-event-emitter-buffer-capacity
    del_configuration spi-events-listener-event-emitter-keycloak-id
    del_configuration spi-events-listener-event-emitter-datacenter-id
    del_configuration spi-events-listener-event-emitter-connect-timeout-millis
    del_configuration spi-events-listener-event-emitter-connection-request-timeout-millis
    del_configuration spi-events-listener-event-emitter-socket-timeout-millis

    del_configuration spi-events-listener-kafka-event-emitter-buffer-capacity
    del_configuration spi-events-listener-kafka-event-emitter-client-id
    del_configuration spi-events-listener-kafka-event-emitter-bootstrap-servers
    del_configuration spi-events-listener-kafka-event-emitter-event-topic
    del_configuration spi-events-listener-kafka-event-emitter-admin-event-topic
    del_configuration spi-events-listener-kafka-event-emitter-security-protocol
    del_configuration spi-events-listener-kafka-event-emitter-sasl-jaas-config
    del_configuration spi-events-listener-kafka-event-emitter-sasl-oauthbearer-token-endpoint-url
    del_configuration spi-events-listener-kafka-event-emitter-keycloak-id
    del_configuration spi-events-listener-kafka-event-emitter-datacenter-id

    rm -rf $argv__KEYCLOAK/providers/$JAR_NAME

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
    cp $JAR_PATH $argv__KEYCLOAK/providers/

    # configure module
    # add SPI configuration for events listeners (if not already there)
    add_configuration spi-events-listener-event-emitter-enabled true
    add_configuration spi-events-listener-event-emitter-format FLATBUFFER
    add_configuration spi-events-listener-event-emitter-target-uri "$argv__TARGET"
    add_configuration spi-events-listener-event-emitter-buffer-capacity 10
    add_configuration spi-events-listener-event-emitter-keycloak-id 1
    add_configuration spi-events-listener-event-emitter-datacenter-id 1
    add_configuration spi-events-listener-event-emitter-connect-timeout-millis 500
    add_configuration spi-events-listener-event-emitter-connection-request-timeout-millis 500
    add_configuration spi-events-listener-event-emitter-socket-timeout-millis 500

    add_configuration spi-events-listener-kafka-event-emitter-buffer-capacity 50
    add_configuration spi-events-listener-kafka-event-emitter-client-id keycloak
    add_configuration spi-events-listener-kafka-event-emitter-bootstrap-servers kafka:29093
    add_configuration spi-events-listener-kafka-event-emitter-event-topic keycloak-event
    add_configuration spi-events-listener-kafka-event-emitter-admin-event-topic keycloak-admin-event
    add_configuration spi-events-listener-kafka-event-emitter-security-protocol SASL_PLAINTEXT
    add_configuration spi-events-listener-kafka-event-emitter-sasl-jaas-config org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required clientId="kafka-client" clientSecret="qvFJyeuOrUCxcZnvlj78jBhoiOmigsln" scope="profile";
    add_configuration spi-events-listener-kafka-event-emitter-sasl-oauthbearer-token-endpoint-url http://keycloak.local:8080/auth/realms/kafka/protocol/openid-connect/token
    add_configuration spi-events-listener-kafka-event-emitter-sasl-mechanism OAUTHBEARER
    add_configuration spi-events-listener-kafka-event-emitter-keycloak-id 1
    add_configuration spi-events-listener-kafka-event-emitter-datacenter-id 1
    $argv__KEYCLOAK/bin/$KC_EXE build

    exit 0
}

Main__main "$@"
