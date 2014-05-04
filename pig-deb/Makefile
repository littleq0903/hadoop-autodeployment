default_target: rpm
all: rpm
deb: pig.deb
rpm: pig-0.12.1-1.x86_64.rpm pig.deb rpm_pre_install rmp_post_install

pig.deb: md5sums conffiles
	dpkg-deb --build  src/ pig.deb

md5sums:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums

conffiles:
	cd src && find etc/pig/ -type f -printf '/%p\n' > DEBIAN/conffiles

pig-0.12.1-1.x86_64.rpm: pig-0.12.1-1.spec
	cd pig-0.12.1 && rpmbuild --buildroot=/home/atkins/workingspace/hadoop-autodeployment/pig-deb/pig-0.12.1/ -bb --target=x86_64 pig-0.12.1-1.spec

pig-0.12.1-1.spec: pig-0.12.1
	sed -i '9iAutoReqProv: no' pig-0.12.1/pig-0.12.1-1.spec

pig-0.12.1: pig.deb
	rm -rf pig-0.12.1
	alien --to-rpm  -v --generate  pig.deb

rpm_pre_install: 
	cp src/DEBIAN/preinst ./rpm_pre_install.sh

rmp_post_install:
	cp src/DEBIAN/postinst ./rpm_post_install.sh
clean: 
	rm pig.deb
