def call(String buildTag, Boolean isApproved = false) {
    echo "this is the senEmail Step , this the buildTag , ${buildTag}"
    def status_value = (isApproved) ? 'has been approved' : 'need your approval'
    def subject = "[Jenkins] ${buildTag} ${status_value}"

    emailext(
        body: (isApproved) ? "\${SCRIPT, template=\"managed:build_tag_approved\"}" : "Please check the build ${buildTag}",
        mimeType: 'text/html',
        subject: subject.replaceAll('%2F', '/'),
        to: "mina.lmania04@gmail.com",
        attachLog: false
    )
}
