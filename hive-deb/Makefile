default_target: rpm
deb: hive.deb
rpm: hive-0.13.0-1.x86_64.rpm

hive-0.13.0-1.x86_64.rpm: hive.deb rpm_pre_install rmp_post_install
	rm -rf hive-0.13.0
	alien --to-rpm  -v --generate  hadoop.deb
	sed -i '9iAutoReqProv: no' hive-0.13.0/hive-0.13.0-1.spec
	cd hive-0.13.0 && rpmbuild --buildroot=/home/atkins/workingspace/hadoop-autodeployment/hadoop-deb/hive-0.13.0/ -bb --target=x86_64 hive-0.13.0-1.spec
hive.deb:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums
	cd src && find etc/hive/ -type f -printf '/%p\n' > DEBIAN/conffiles
	dpkg-deb --build  src/ hive.deb

rpm_pre_install: 
	cp src/DEBIAN/preinst ./rpm_pre_install.sh

rmp_post_install:
	cp src/DEBIAN/postinst ./rpm_post_install.sh