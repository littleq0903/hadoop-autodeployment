default_target: rpm
rpm: hadoop-2.4.0-1.x86_64.rpm

hadoop-2.4.0-1.x86_64.rpm: hadoop.deb rpm_pre_install rmp_post_install
	rm -rf hadoop-2.4.0
	alien --to-rpm  -v --generate  hadoop.deb
	sed -i '9iAutoReqProv: no' hadoop-2.4.0/hadoop-2.4.0-1.spec
	cd hadoop-2.4.0 && rpmbuild --buildroot=/home/atkins/workingspace/hadoop-autodeployment/hadoop-deb/hadoop-2.4.0/ -bb --target=x86_64 hadoop-2.4.0-1.spec
hadoop.deb: 
	rm -rf hadoop.deb
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums
	cd src && find etc/hadoop/ -type f -printf '/%p\n' > DEBIAN/conffiles
	dpkg-deb --build  src/ hadoop.deb

rpm_pre_install: 
	cp src/DEBIAN/preinst ./rpm_pre_install.sh

rmp_post_install:
	cp src/DEBIAN/postinst ./rpm_post_install.sh
	 
